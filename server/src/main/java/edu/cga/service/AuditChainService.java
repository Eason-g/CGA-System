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
 * hash = SHA-256(prev_hash|seq|targetType|targetId|operatorId|action|opinion|beforeValue|afterValue|timestamp)。
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

    private static final String COLUMNS = "id, target_type, target_id, operator_id, action, opinion, before_value, "
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
        Map<String, Object> head = jdbc.queryForMap(
                "SELECT last_seq, last_hash FROM audit_chain_head WHERE id = 1 FOR UPDATE");
        long seq = ((Number) head.get("last_seq")).longValue();
        String prev = (String) head.get("last_hash");
        for (AuditLog e : entries) {
            seq++;
            String hash = hash(prev, seq, e.getTargetType(), e.getTargetId(), e.getOperatorId(), e.getAction(),
                    e.getOpinion(), e.getBeforeValue(), e.getAfterValue(), e.getTimestamp());
            jdbc.update(INSERT_SQL, e.getId(), e.getTargetType(), e.getTargetId(), e.getOperatorId(),
                    e.getOperatorName(), e.getOperatorRole(), e.getAction(), e.getOpinion(), e.getBeforeValue(),
                    e.getAfterValue(), e.getTimestamp(), seq, prev, hash);
            prev = hash;
        }
        jdbc.update("UPDATE audit_chain_head SET last_seq = ?, last_hash = ? WHERE id = 1", seq, prev);
    }

    // ------------------------------------------------------------------ 初始化（建列、补算种子日志）

    /**
     * 启动/重建后调用：老库缺列时补列和链头表；再把 seq 为空的日志（种子数据）按时间顺序接到链尾补算哈希。幂等。
     */
    public void initialize() {
        ensureSchema();
        int n = newTx.execute(s -> backfill());
        if (n > 0) {
            log.info("已为 {} 条审计日志补算哈希链", n);
        }
    }

    /** 兼容 P0 时建的库：audit_log 没有链字段、没有链头表时补上 */
    private void ensureSchema() {
        Integer cols = jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns "
                + "WHERE table_schema = DATABASE() AND table_name = 'audit_log' AND column_name = 'seq'", Integer.class);
        if (cols == null || cols == 0) {
            log.info("audit_log 缺少哈希链字段，执行补列");
            jdbc.execute("ALTER TABLE audit_log ADD COLUMN seq BIGINT NULL, ADD COLUMN prev_hash CHAR(64) NULL, "
                    + "ADD COLUMN hash CHAR(64) NULL, ADD UNIQUE KEY uk_audit_seq (seq)");
        }
        jdbc.execute("CREATE TABLE IF NOT EXISTS audit_chain_head (id TINYINT NOT NULL, "
                + "last_seq BIGINT NOT NULL DEFAULT 0, last_hash CHAR(64) NOT NULL, PRIMARY KEY (id)) "
                + "ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审计哈希链链头'");
        Integer heads = jdbc.queryForObject("SELECT COUNT(*) FROM audit_chain_head WHERE id = 1", Integer.class);
        if (heads == null || heads == 0) {
            // 链头丢失时从已有链尾恢复，避免新日志与已有序号冲突
            List<Map<String, Object>> tail = jdbc.queryForList(
                    "SELECT seq, hash FROM audit_log WHERE seq IS NOT NULL ORDER BY seq DESC LIMIT 1");
            long lastSeq = tail.isEmpty() ? 0 : ((Number) tail.get(0).get("seq")).longValue();
            String lastHash = tail.isEmpty() ? GENESIS_HASH : (String) tail.get(0).get("hash");
            jdbc.update("INSERT IGNORE INTO audit_chain_head (id, last_seq, last_hash) VALUES (1, ?, ?)",
                    lastSeq, lastHash);
        }
    }

    private int backfill() {
        Map<String, Object> head = jdbc.queryForMap(
                "SELECT last_seq, last_hash FROM audit_chain_head WHERE id = 1 FOR UPDATE");
        List<Row> rows = jdbc.query("SELECT " + COLUMNS + " FROM audit_log WHERE seq IS NULL "
                + "ORDER BY log_time IS NULL, log_time, id", (rs, i) -> Row.of(rs));
        if (rows.isEmpty()) {
            return 0;
        }
        long seq = ((Number) head.get("last_seq")).longValue();
        String prev = (String) head.get("last_hash");
        for (Row r : rows) {
            seq++;
            String hash = hash(prev, seq, r.targetType, r.targetId, r.operatorId, r.action, r.opinion,
                    r.beforeValue, r.afterValue, r.logTime);
            jdbc.update("UPDATE audit_log SET seq = ?, prev_hash = ?, hash = ? WHERE id = ?", seq, prev, hash, r.id);
            prev = hash;
        }
        jdbc.update("UPDATE audit_chain_head SET last_seq = ?, last_hash = ? WHERE id = 1", seq, prev);
        return rows.size();
    }

    // ------------------------------------------------------------------ 校验

    /** 校验结果：ok、已检查条数、第一处不符（{ id, seq, reason, detail }，通过时为 null） */
    public record VerifyResult(boolean ok, int checked, Map<String, Object> brokenAt) {
    }

    /**
     * 从第一条起按 seq 重算整条链。每条依次检查：序号连续 → prev_hash 等于上一条 hash → 重算 hash 一致；
     * 最后核对链头，发现链尾被删。只报告第一处不符。
     */
    public VerifyResult verify() {
        return readTx.execute(s -> {
            List<Row> rows = jdbc.query("SELECT " + COLUMNS + " FROM audit_log ORDER BY seq IS NULL, seq, id",
                    (rs, i) -> Row.of(rs));
            long expectedSeq = 1;
            String prev = GENESIS_HASH;
            int checked = 0;
            for (Row r : rows) {
                if (r.seq == null || r.hash == null) {
                    return broken(checked, r.id, r.seq, REASON_LINK, "该日志未接入哈希链（缺少序号或哈希），可能是绕过系统直接插入的");
                }
                if (r.seq != expectedSeq) {
                    return broken(checked, r.id, r.seq, REASON_GAP,
                            "期望序号 " + expectedSeq + "，实际为 " + r.seq + "，中间的日志可能已被删除");
                }
                if (!prev.equals(r.prevHash)) {
                    return broken(checked, r.id, r.seq, REASON_LINK, "prev_hash 与上一条日志的哈希不一致");
                }
                String actual = hash(r.prevHash, r.seq, r.targetType, r.targetId, r.operatorId, r.action, r.opinion,
                        r.beforeValue, r.afterValue, r.logTime);
                if (!actual.equals(r.hash)) {
                    return broken(checked, r.id, r.seq, REASON_CONTENT, "按当前内容重算的哈希与存档哈希不一致");
                }
                checked++;
                expectedSeq++;
                prev = r.hash;
            }
            List<Map<String, Object>> head = jdbc.queryForList(
                    "SELECT last_seq, last_hash FROM audit_chain_head WHERE id = 1");
            if (!head.isEmpty()) {
                long lastSeq = ((Number) head.get(0).get("last_seq")).longValue();
                if (lastSeq != expectedSeq - 1) {
                    return broken(checked, null, expectedSeq, REASON_GAP,
                            "链头记录共 " + lastSeq + " 条，实际只有 " + (expectedSeq - 1) + " 条，末尾的日志可能已被删除");
                }
                if (!prev.equals(head.get(0).get("last_hash"))) {
                    return broken(checked, null, lastSeq, REASON_LINK, "链头哈希与最后一条日志不一致");
                }
            }
            return new VerifyResult(true, checked, null);
        });
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

    /** hash = SHA-256(prev_hash|seq|targetType|targetId|operatorId|action|opinion|beforeValue|afterValue|timestamp)，空值按空串 */
    public static String hash(String prevHash, long seq, String targetType, String targetId, String operatorId,
                              String action, String opinion, String beforeValue, String afterValue,
                              LocalDateTime timestamp) {
        String ts = timestamp == null ? "" : timestamp.format(TimeService.TIMESTAMP_FORMAT);
        String text = String.join("|", nz(prevHash), String.valueOf(seq), nz(targetType), nz(targetId),
                nz(operatorId), nz(action), nz(opinion), nz(beforeValue), nz(afterValue), ts);
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JDK 不支持 SHA-256", e);
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    /** 校验/补算时读出的一行 */
    private record Row(String id, String targetType, String targetId, String operatorId, String action,
                       String opinion, String beforeValue, String afterValue, LocalDateTime logTime, Long seq,
                       String prevHash, String hash) {
        static Row of(ResultSet rs) throws SQLException {
            long rawSeq = rs.getLong("seq");
            Long seq = rs.wasNull() ? null : rawSeq;
            return new Row(rs.getString("id"), rs.getString("target_type"), rs.getString("target_id"),
                    rs.getString("operator_id"), rs.getString("action"), rs.getString("opinion"),
                    rs.getString("before_value"), rs.getString("after_value"),
                    rs.getObject("log_time", LocalDateTime.class), seq,
                    rs.getString("prev_hash"), rs.getString("hash"));
        }
    }
}
