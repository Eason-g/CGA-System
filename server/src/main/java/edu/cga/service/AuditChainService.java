package edu.cga.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import edu.cga.common.TimeService;
import edu.cga.domain.entity.AuditLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 审计日志哈希链（P1 第二批 2.2）。
 * <p>
 * 每条日志带 seq（从 1 连续递增）、prev_hash（上一条的 hash，首条为 64 个 0）和
 * 第 2 版摘要覆盖记录 ID、前驱哈希、序号及全部业务字段，并保留字段边界和空值。
 * <p>
 * <b>追加</b>：先 {@code SELECT ... FROM audit_chain_head WHERE id = 1 FOR UPDATE} 锁住单行链头，
 * 再按链头分配序号、计算哈希、插入日志、推进链头。并发写入时链头锁让追加串行化，链不会分叉；
 * seq 上还有唯一索引兜底。
 * <p>
 * <b>为什么在事务提交前才追加</b>：业务事务里调用 {@link #append} 时，日志先暂存在当前事务上，
 * 到 beforeCommit 阶段才锁链头并写入。这样链头锁总是一个事务里最后获取的锁、只持有到提交为止，
 * 不会和业务里先锁经费/学生/岗位行的顺序交叉形成死锁，也不会让整段业务都排队等链头。
 * 业务回滚时暂存的日志一起丢弃，序号不会出现空洞。没有事务时（如初始化）立即在新事务里追加。
 * <p>
 * 说明：哈希链能<b>发现</b>篡改（改内容、删记录、改链指针），但不能<b>阻止</b>拥有数据库权限的人篡改——
 * 有权限的人可以把整条链从被改处起全部重算。如需防住这一点，还要把链头哈希定期存证到库外。
 */
@Service
public class AuditChainService {

    private static final Logger log = LoggerFactory.getLogger(AuditChainService.class);

    /** 首条日志的 prev_hash */
    public static final String GENESIS_HASH = "0".repeat(64);

    public static final String REASON_CONTENT = "内容被改";
    public static final String REASON_LINK = "链断开";
    public static final String REASON_GAP = "被删除导致序号不连续";

    private static final String INSERT_SQL = "INSERT INTO audit_log (id, target_type, target_id, operator_id, "
            + "operator_name, operator_role, action, opinion, before_value, after_value, log_time, seq, prev_hash, hash) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String COLUMNS = "id, target_type, target_id, operator_id, operator_name, operator_role, action, opinion, before_value, "
            + "after_value, log_time, seq, prev_hash, hash";

    /** 当前事务上暂存的待追加日志（TransactionSynchronizationManager 的资源键） */
    private final Object pendingKey = new Object();

    private final JdbcTemplate jdbc;
    private final TransactionTemplate newTx;
    private final TransactionTemplate readTx;

    public AuditChainService(DataSource dataSource, PlatformTransactionManager txManager) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.newTx = new TransactionTemplate(txManager);
        this.newTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.newTx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.readTx = new TransactionTemplate(txManager);
        this.readTx.setReadOnly(true);
        this.readTx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }

    // ------------------------------------------------------------------ 追加

    /**
     * 把一条日志追加到链上。在业务事务内时延后到提交前写入（与业务数据一起提交或回滚）；否则立即在新事务中写入。
     * 调用方应已把 timestamp 截断到秒（与 DATETIME 列精度一致，否则重算哈希时对不上）。
     */
    public void append(AuditLog entry) {
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            pendingList().add(entry);
            return;
        }
        newTx.executeWithoutResult(s -> appendNow(List.of(entry)));
    }

    @SuppressWarnings("unchecked")
    private List<AuditLog> pendingList() {
        List<AuditLog> pending = (List<AuditLog>) TransactionSynchronizationManager.getResource(pendingKey);
        if (pending == null) {
            List<AuditLog> list = new ArrayList<>();
            TransactionSynchronizationManager.bindResource(pendingKey, list);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void beforeCommit(boolean readOnly) {
                    if (!list.isEmpty()) {
                        appendNow(list);
                    }
                }

                @Override
                public void afterCompletion(int status) {
                    TransactionSynchronizationManager.unbindResourceIfPossible(pendingKey);
                }
            });
            pending = list;
        }
        return pending;
    }

    /** 锁链头 → 逐条分配序号与哈希并插入 → 推进链头。必须在事务内调用。 */
    private void appendNow(List<AuditLog> entries) {
        Map<String, Object> head = lockHead();
        if (head == null || version(head) != 2) {
            throw new IllegalStateException("审计链头缺失或尚未完成完整性升级");
        }
        long seq = ((Number) head.get("last_seq")).longValue();
        String prev = (String) head.get("last_hash");
        for (AuditLog e : entries) {
            seq++;
            String hash = hash(2, prev, seq, e.getId(), e.getTargetType(), e.getTargetId(), e.getOperatorId(), e.getOperatorName(), e.getOperatorRole(), e.getAction(),
                    e.getOpinion(), e.getBeforeValue(), e.getAfterValue(), e.getTimestamp());
            jdbc.update(INSERT_SQL, e.getId(), e.getTargetType(), e.getTargetId(), e.getOperatorId(),
                    e.getOperatorName(), e.getOperatorRole(), e.getAction(), e.getOpinion(), e.getBeforeValue(),
                    e.getAfterValue(), e.getTimestamp(), seq, prev, hash);
            prev = hash;
        }
        jdbc.update("UPDATE audit_chain_head SET last_seq = ?, last_hash = ? WHERE id = 1", seq, prev);
    }

    // ------------------------------------------------------------------ 初始化（建列、补算种子日志）

    /** 启动时只迁移确认为旧结构的库，有链字段却缺少链头时留给校验报告。 */
    public void initialize() {
        initialize(false);
    }

    /** 只在本次建表并导入已知种子后，允许为种子创建摘要。 */
    public void initializeSeed() {
        initialize(true);
    }

    private void initialize(boolean seed) {
        boolean preChain = ensureSchema();
        newTx.executeWithoutResult(status -> {
            if (preChain) {
                jdbc.update("INSERT INTO audit_chain_head (id, last_seq, last_hash, hash_version) VALUES (1, 0, ?, 2)",
                        GENESIS_HASH);
            }
            Map<String, Object> head = lockHead();
            if (head == null) {
                return;
            }
            if (preChain || seed) {
                List<Row> rows = jdbc.query("SELECT " + COLUMNS + " FROM audit_log "
                        + "ORDER BY log_time IS NULL, log_time, id", (rs, i) -> Row.of(rs));
                rewriteChain(rows);
            } else if (version(head) == 1) {
                List<Row> rows = chainRows();
                if (verifyRows(rows, head).ok()) {
                    rewriteChain(rows);
                } else {
                    log.error("旧审计链校验失败，保留原摘要和链头");
                }
            }
        });
    }

    private boolean ensureSchema() {
        boolean preChain = !columnExists("audit_log", "seq");
        if (preChain) {
            jdbc.execute("ALTER TABLE audit_log ADD COLUMN seq BIGINT NULL, ADD COLUMN prev_hash CHAR(64) NULL, "
                    + "ADD COLUMN hash CHAR(64) NULL, ADD UNIQUE KEY uk_audit_seq (seq)");
        }
        jdbc.execute("CREATE TABLE IF NOT EXISTS audit_chain_head (id TINYINT NOT NULL, "
                + "last_seq BIGINT NOT NULL DEFAULT 0, last_hash CHAR(64) NOT NULL, hash_version TINYINT NOT NULL DEFAULT 2, "
                + "PRIMARY KEY (id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审计哈希链链头'");
        if (!columnExists("audit_chain_head", "hash_version")) {
            jdbc.execute("ALTER TABLE audit_chain_head ADD COLUMN hash_version TINYINT NOT NULL DEFAULT 1");
        }
        return preChain;
    }

    private boolean columnExists(String table, String column) {
        Integer cols = jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns "
                + "WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?", Integer.class, table, column);
        return cols != null && cols > 0;
    }

    private Map<String, Object> lockHead() {
        return head(" FOR UPDATE");
    }

    private Map<String, Object> head(String lock) {
        return jdbc.queryForList("SELECT last_seq, last_hash, hash_version FROM audit_chain_head WHERE id = 1" + lock)
                .stream().findFirst().orElse(null);
    }

    private static int version(Map<String, Object> head) {
        return ((Number) head.get("hash_version")).intValue();
    }

    private List<Row> chainRows() {
        return jdbc.query("SELECT " + COLUMNS + " FROM audit_log ORDER BY seq IS NULL, seq, id", (rs, i) -> Row.of(rs));
    }

    /** 只对可信种子或通过旧算法校验的完整链重新编码，迁移与链头更新同事务。 */
    private void rewriteChain(List<Row> rows) {
        long seq = 0;
        String prev = GENESIS_HASH;
        for (Row r : rows) {
            seq++;
            String digest = r.digest(2, prev, seq);
            jdbc.update("UPDATE audit_log SET seq = ?, prev_hash = ?, hash = ? WHERE id = ?", seq, prev, digest, r.id);
            prev = digest;
        }
        jdbc.update("UPDATE audit_chain_head SET last_seq = ?, last_hash = ?, hash_version = 2 WHERE id = 1", seq, prev);
    }

    /** 校验结果：ok、已检查条数、第一处不符。 */
    public record VerifyResult(boolean ok, int checked, Map<String, Object> brokenAt) {
    }

    public VerifyResult verify() {
        return readTx.execute(status -> verifyRows(chainRows(), head("")));
    }

    private VerifyResult verifyRows(List<Row> rows, Map<String, Object> head) {
        if (head == null) {
            return broken(0, null, null, REASON_LINK, "缺少审计链头");
        }
        int version = version(head);
        if (version != 1 && version != 2) {
            return broken(0, null, null, REASON_LINK, "无法识别审计哈希版本");
        }
        long expectedSeq = 1;
        String prev = GENESIS_HASH;
        int checked = 0;
        for (Row r : rows) {
            if (r.seq == null || r.hash == null) {
                return broken(checked, r.id, r.seq, REASON_LINK, "该日志未接入哈希链（缺少序号或哈希）");
            }
            if (r.seq != expectedSeq) {
                return broken(checked, r.id, r.seq, REASON_GAP,
                        "期望序号 " + expectedSeq + "，实际为 " + r.seq + "，中间的日志可能已被删除");
            }
            if (!prev.equals(r.prevHash)) {
                return broken(checked, r.id, r.seq, REASON_LINK, "prev_hash 与上一条日志的哈希不一致");
            }
            if (!r.digest(version, prev, expectedSeq).equals(r.hash)) {
                return broken(checked, r.id, r.seq, REASON_CONTENT, "按当前内容重算的哈希与存档哈希不一致");
            }
            checked++;
            expectedSeq++;
            prev = r.hash;
        }
        long lastSeq = ((Number) head.get("last_seq")).longValue();
        if (lastSeq != expectedSeq - 1) {
            return broken(checked, null, expectedSeq, REASON_GAP,
                    "链头记录共 " + lastSeq + " 条，实际只有 " + (expectedSeq - 1) + " 条，末尾的日志可能已被删除");
        }
        if (!prev.equals(head.get("last_hash"))) {
            return broken(checked, null, lastSeq, REASON_LINK, "链头哈希与最后一条日志不一致");
        }
        return new VerifyResult(true, checked, null);
    }

    private static VerifyResult broken(int checked, String id, Long seq, String reason, String detail) {
        Map<String, Object> at = new LinkedHashMap<>();
        at.put("id", id);
        at.put("seq", seq);
        at.put("reason", reason);
        at.put("detail", detail);
        return new VerifyResult(false, checked, at);
    }

    // ------------------------------------------------------------------ 哈希

    private static String hash(int version, String prevHash, long seq, String id, String targetType,
                               String targetId, String operatorId, String operatorName, String operatorRole,
                               String action, String opinion, String beforeValue, String afterValue,
                               LocalDateTime timestamp) {
        String ts = timestamp == null ? null : timestamp.format(TimeService.TIMESTAMP_FORMAT);
        String[] fields = { prevHash, String.valueOf(seq), targetType, targetId, operatorId, operatorName,
                operatorRole, action, opinion, beforeValue, afterValue, ts };
        StringBuilder payload = new StringBuilder();
        if (version == 2) {
            payload.append("2:");
            appendField(payload, id);
            for (String field : fields) {
                appendField(payload, field);
            }
        } else {
            // 旧格式仅用来在迁移前核对已有链头。
            for (int i = 0; i < fields.length; i++) {
                if (i > 0) {
                    payload.append('|');
                }
                if (fields[i] != null) {
                    payload.append(fields[i]);
                }
            }
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(payload.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JDK 不支持 SHA-256", e);
        }
    }

    private static void appendField(StringBuilder payload, String field) {
        if (field == null) {
            payload.append("-1:");
        } else {
            payload.append(field.length()).append(':').append(field);
        }
    }

    /** 校验/补算时读出的一行 */
    private record Row(String id, String targetType, String targetId, String operatorId, String operatorName,
                       String operatorRole, String action, String opinion, String beforeValue, String afterValue,
                       LocalDateTime logTime, Long seq, String prevHash, String hash) {
        String digest(int version, String prev, long seq) {
            return AuditChainService.hash(version, prev, seq, id, targetType, targetId, operatorId, operatorName,
                    operatorRole, action, opinion, beforeValue, afterValue, logTime);
        }

        static Row of(ResultSet rs) throws SQLException {
            long rawSeq = rs.getLong("seq");
            Long seq = rs.wasNull() ? null : rawSeq;
            return new Row(rs.getString("id"), rs.getString("target_type"), rs.getString("target_id"),
                    rs.getString("operator_id"), rs.getString("operator_name"), rs.getString("operator_role"),
                    rs.getString("action"), rs.getString("opinion"),
                    rs.getString("before_value"), rs.getString("after_value"),
                    rs.getObject("log_time", LocalDateTime.class), seq,
                    rs.getString("prev_hash"), rs.getString("hash"));
        }
    }
}
