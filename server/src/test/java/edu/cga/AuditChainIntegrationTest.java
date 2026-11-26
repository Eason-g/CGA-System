package edu.cga;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import edu.cga.security.CurrentUser;
import edu.cga.service.AuditChainService;
import edu.cga.service.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

/**
 * 审计日志防篡改（P1 第二批 2.2）：哈希链补算、校验接口、直接改库能被发现、并发追加不分叉、权限。
 */
class AuditChainIntegrationTest extends IntegrationTestBase {

    private static final String VERIFY = "/api/audit/verify";

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private AuditService auditService;
    @Autowired
    private PlatformTransactionManager txManager;
    @Autowired
    private AuditChainService auditChain;

    /** 每个用例都从干净的种子数据开始（篡改用例会破坏链） */
    @BeforeEach
    void reset() {
        databaseInitializer.rebuild();
    }

    private JsonNode verify() throws Exception {
        return send(HttpMethod.GET, VERIFY, "zhou.guoqiang", null, 200).path("data");
    }

    private long logCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM audit_log", Long.class);
    }

    private String idAtSeq(long seq) {
        return jdbc.queryForObject("SELECT id FROM audit_log WHERE seq = ?", String.class, seq);
    }

    @Test
    void seedLogsAreBackfilledAndChainVerifies() throws Exception {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE seq IS NULL OR hash IS NULL", Long.class))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT prev_hash FROM audit_log WHERE seq = 1", String.class))
                .isEqualTo(AuditChainService.GENESIS_HASH);
        // 种子日志按时间顺序入链：最早的一条（9 月）是 seq 1
        assertThat(idAtSeq(1)).isEqualTo("LOG_07");
        Map<String, Object> head = jdbc.queryForMap("SELECT last_seq, last_hash FROM audit_chain_head WHERE id = 1");
        assertThat(((Number) head.get("last_seq")).longValue()).isEqualTo(logCount());

        JsonNode r = verify();
        assertThat(r.path("ok").asBoolean()).isTrue();
        assertThat(r.path("checked").asLong()).isEqualTo(logCount());
        assertThat(r.path("brokenAt").isNull()).isTrue();
    }

    @Test
    void businessWritesKeepChainIntact() throws Exception {
        long before = logCount();
        apiPut("/api/users/me", "lin.zhiyuan",
                Map.of("email", "lin@univ.edu.cn", "phone", "13800000000", "skills", List.of("Java"), "bio", "测试"),
                200);
        apiPut("/api/users/me", "alex.chen", Map.of("bio", "第二次业务写入"), 200);
        assertThat(logCount()).isGreaterThan(before);
        JsonNode r = verify();
        assertThat(r.path("ok").asBoolean()).isTrue();
        assertThat(r.path("checked").asLong()).isEqualTo(logCount());
    }

    @Test
    void modifiedOpinionIsReportedAsContentChange() throws Exception {
        jdbc.update("UPDATE audit_log SET opinion = '被人悄悄改过的意见' WHERE id = 'LOG_05'");
        JsonNode r = verify();
        assertThat(r.path("ok").asBoolean()).isFalse();
        assertThat(r.path("brokenAt").path("id").asString()).isEqualTo("LOG_05");
        assertThat(r.path("brokenAt").path("reason").asString()).isEqualTo(AuditChainService.REASON_CONTENT);
    }

    @Test
    void deletedLogIsReportedAsSequenceGap() throws Exception {
        String next = idAtSeq(6);
        jdbc.update("DELETE FROM audit_log WHERE seq = 5");
        JsonNode r = verify();
        assertThat(r.path("ok").asBoolean()).isFalse();
        assertThat(r.path("checked").asLong()).isEqualTo(4);
        assertThat(r.path("brokenAt").path("id").asString()).isEqualTo(next);
        assertThat(r.path("brokenAt").path("reason").asString()).isEqualTo(AuditChainService.REASON_GAP);
    }

    @Test
    void deletedLastLogIsReportedByChainHead() throws Exception {
        long last = logCount();
        jdbc.update("DELETE FROM audit_log WHERE seq = ?", last);
        JsonNode r = verify();
        assertThat(r.path("ok").asBoolean()).isFalse();
        assertThat(r.path("brokenAt").path("reason").asString()).isEqualTo(AuditChainService.REASON_GAP);
        assertThat(r.path("brokenAt").path("seq").asLong()).isEqualTo(last);
    }

    @Test
    void changedPrevHashIsReportedAsBrokenLink() throws Exception {
        String target = idAtSeq(3);
        jdbc.update("UPDATE audit_log SET prev_hash = REPEAT('a', 64) WHERE seq = 3");
        JsonNode r = verify();
        assertThat(r.path("ok").asBoolean()).isFalse();
        assertThat(r.path("brokenAt").path("id").asString()).isEqualTo(target);
        assertThat(r.path("brokenAt").path("reason").asString()).isEqualTo(AuditChainService.REASON_LINK);
    }

    @Test
    void rolledBackTransactionLeavesNoGap() throws Exception {
        long before = logCount();
        TransactionTemplate tx = new TransactionTemplate(txManager);
        tx.executeWithoutResult(status -> {
            auditService.log("CONFIG", "ROLLBACK_TEST", operator(), "回滚测试", "不应落库", "", "");
            status.setRollbackOnly();
        });
        assertThat(logCount()).isEqualTo(before);
        tx.executeWithoutResult(status ->
                auditService.log("CONFIG", "AFTER_ROLLBACK", operator(), "回滚后追加", "", "", ""));
        assertThat(jdbc.queryForObject("SELECT seq FROM audit_log WHERE target_id = 'AFTER_ROLLBACK'", Long.class))
                .isEqualTo(before + 1);
        assertThat(verify().path("ok").asBoolean()).isTrue();
    }

    /** 20 个线程各自独立事务同时写日志：链不分叉、序号连续、校验通过 */
    @Test
    void concurrentAppendsKeepChainLinear() throws Exception {
        long before = logCount();
        int threads = 20;
        TransactionTemplate tx = new TransactionTemplate(txManager);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                int n = i;
                futures.add(pool.submit(() -> {
                    start.await();
                    tx.executeWithoutResult(status -> auditService.log("CONFIG", "CONCURRENT_" + n, operator(),
                            "并发写入测试", "第 " + n + " 条", "", ""));
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> f : futures) {
                f.get(60, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        long after = logCount();
        assertThat(after).isEqualTo(before + threads);
        assertThat(jdbc.queryForObject("SELECT MAX(seq) FROM audit_log", Long.class)).isEqualTo(after);
        assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT seq) FROM audit_log", Long.class)).isEqualTo(after);
        assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT prev_hash) FROM audit_log", Long.class)).isEqualTo(after);
        JsonNode r = verify();
        assertThat(r.path("ok").asBoolean()).as(r.toString()).isTrue();
        assertThat(r.path("checked").asLong()).isEqualTo(after);
    }

    /** P0 时建的老库（没有链字段和链头表）：启动初始化时补列并补算整条链 */
    @Test
    void legacyDatabaseIsMigrated() throws Exception {
        jdbc.execute("DROP TABLE audit_chain_head");
        jdbc.execute("ALTER TABLE audit_log DROP INDEX uk_audit_seq, DROP COLUMN seq, DROP COLUMN prev_hash, DROP COLUMN hash");
        auditChain.initialize();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE seq IS NULL", Long.class)).isZero();
        apiPut("/api/users/me", "lin.zhiyuan", Map.of("bio", "迁移后写入"), 200);
        JsonNode r = verify();
        assertThat(r.path("ok").asBoolean()).isTrue();
        assertThat(r.path("checked").asLong()).isEqualTo(logCount());
    }

    @Test
    void onlySchoolAdminCanVerify() throws Exception {
        for (String account : List.of("li.wanqing", "zhang.mingyuan", "wang.haitao", "alex.chen")) {
            send(HttpMethod.GET, VERIFY, account, null, 403);
        }
        send(HttpMethod.GET, VERIFY, null, null, 401);
    }

    private static CurrentUser operator() {
        return new CurrentUser("U_SCHOOL_ADMIN", "zhou.guoqiang", "周国强", "SCHOOL_ADMIN", "校级系统/财务管理员",
                "DEPT_OFFICE", false);
    }
}
