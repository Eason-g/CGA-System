package edu.cga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import edu.cga.domain.mapper.NotificationMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.JsonNode;

/**
 * 快照结构与种子数据一致性：校级管理员视角下各集合条数与 seedData.js 一致，字段名与 mock 的 db 对象一致。
 * <p>
 * 期望条数取自 data.sql 头部的 “seed-counts” 行（由 scripts/gen-seed-sql.mjs 根据 seedData.js 写入）。
 */
class SnapshotIntegrationTest extends IntegrationTestBase {

    @Autowired
    private NotificationMapper notificationMapper;

    private static Map<String, Integer> seedCounts() throws Exception {
        var res = new ClassPathResource("db/data.sql");
        try (var reader = new BufferedReader(new InputStreamReader(res.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                int idx = line.indexOf("seed-counts:");
                if (idx >= 0) {
                    Map<String, Integer> counts = new LinkedHashMap<>();
                    for (String kv : line.substring(idx + "seed-counts:".length()).trim().split("\\s+")) {
                        String[] p = kv.split("=");
                        counts.put(p[0], Integer.parseInt(p[1]));
                    }
                    return counts;
                }
            }
        }
        throw new IllegalStateException("data.sql 中缺少 seed-counts 行，请重新运行 scripts/gen-seed-sql.mjs");
    }

    @Test
    void schoolAdminSnapshotMatchesSeedCounts() throws Exception {
        Map<String, Integer> expected = seedCounts();
        assertThat(expected).containsKeys("jobs", "users", "auditLogs");
        JsonNode db = snapshotAs("zhou.guoqiang");

        // 与 mock db 相同的顶层结构
        assertThat(db.propertyNames()).containsExactly("meta", "departments", "users", "budgets", "configs", "jobs",
                "applications", "contracts", "timesheets", "evaluations", "payrolls", "notifications", "auditLogs");

        expected.forEach((collection, count) -> {
            if (collection.equals("notifications")) {
                return; // 通知只含本人的，单独断言
            }
            assertThat(db.path(collection).size()).as(collection).isEqualTo(count);
        });

        // 通知：库里条数与种子一致，但校级管理员的快照里只有发给他本人的（种子中没有）
        assertThat(notificationMapper.selectCount(null)).isEqualTo(expected.get("notifications").longValue());
        assertThat(db.path("notifications").size()).isZero();
    }

    @Test
    void snapshotShapeMatchesMockDb() throws Exception {
        String token = login("zhou.guoqiang");
        String raw = mvc.perform(get("/api/snapshot").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(raw).doesNotContain("passwordHash").doesNotContain("operatorId");
        JsonNode db = json.readTree(raw).path("data");

        // meta：版本 + 由 TimeService 推算的周次、月份（app.today=2026-11-20）
        JsonNode meta = db.path("meta");
        assertThat(meta.path("version").asString()).isEqualTo("1.1.0");
        assertThat(meta.path("currentWeekNo").asInt()).isEqualTo(12);
        assertThat(meta.path("currentMonth").asString()).isEqualTo("2026-11");
        assertThat(meta.path("today").asString()).isEqualTo("2026-11-20");

        // configs 三项
        JsonNode configs = db.path("configs");
        assertThat(configs.propertyNames()).containsExactlyInAnyOrder("workflows", "evaluationTemplates", "globalRules");
        assertThat(configs.path("workflows").path("TA").size()).isEqualTo(4);
        assertThat(configs.path("globalRules").path("maxWeeklyHoursIntl").asInt()).isEqualTo(20);

        // 岗位：JSON 列、日期格式、预留预算
        JsonNode job01 = find(db.path("jobs"), "JOB_01");
        assertThat(job01.path("workflowSteps").isArray()).isTrue();
        assertThat(job01.path("extraFields").path("minLangScore").asDouble()).isEqualTo(6.5);
        assertThat(job01.path("createdAt").asString()).isEqualTo("2026-10-12 09:30:00");
        assertThat(job01.path("deadline").asString()).isEqualTo("2026-12-10");
        assertThat(job01.path("allowInternational").asBoolean()).isTrue();
        // 种子数据满足后端的预算不变量：有岗位的经费，冻结额 = Σ max(岗位预留 − 该岗位已发放, 0)
        for (JsonNode budget : db.path("budgets")) {
            String budgetId = budget.path("id").asString();
            java.math.BigDecimal expected = java.math.BigDecimal.ZERO;
            boolean hasJobs = false;
            for (JsonNode job : db.path("jobs")) {
                if (!budgetId.equals(job.path("budgetId").asString()) || "REJECTED".equals(job.path("status").asString())) {
                    continue;
                }
                hasJobs = true;
                java.math.BigDecimal paid = java.math.BigDecimal.ZERO;
                for (JsonNode p : db.path("payrolls")) {
                    if (job.path("id").asString().equals(p.path("jobId").asString())
                            && "PAID".equals(p.path("financeStatus").asString())) {
                        paid = paid.add(p.path("amount").decimalValue());
                    }
                }
                expected = expected.add(job.path("reservedAmount").decimalValue().subtract(paid)
                        .max(java.math.BigDecimal.ZERO));
            }
            if (hasJobs) {
                assertThat(budget.path("frozenAmount").decimalValue()).as("经费 %s 的冻结额", budgetId)
                        .isEqualByComparingTo(expected);
            }
        }

        // 申请：嵌套对象与布尔字段名
        JsonNode app06 = find(db.path("applications"), "APP_06");
        assertThat(app06.path("isSpecialApproval").asBoolean()).isTrue();
        assertThat(app06.path("complianceCheckResult").path("checks").isArray()).isTrue();
        assertThat(app06.has("interviewInfo")).isTrue();

        // 用户：校级管理员看到明文联系方式
        JsonNode alex = find(db.path("users"), "U_INTL_STU");
        assertThat(alex.path("isInternational").asBoolean()).isTrue();
        assertThat(alex.path("phone").asString()).isEqualTo("138-0010-2091");
        assertThat(alex.path("skills").isArray()).isTrue();

        // 审计日志字段 timestamp
        JsonNode log01 = find(db.path("auditLogs"), "LOG_01");
        assertThat(log01.path("timestamp").asString()).isEqualTo("2026-10-12 09:30:00");

        for (String key : List.of("evaluations", "payrolls", "timesheets", "contracts", "budgets")) {
            assertThat(db.path(key).isArray()).as(key).isTrue();
        }
    }

    @Test
    void studentNotificationsUseReadField() throws Exception {
        JsonNode db = snapshotAs("alex.chen");
        assertThat(db.path("notifications").size()).isEqualTo(2);
        JsonNode n = db.path("notifications").get(0);
        assertThat(n.has("read")).isTrue();
        assertThat(n.path("read").asBoolean()).isFalse();
    }

    private static JsonNode find(JsonNode array, String id) {
        for (JsonNode n : array) {
            if (id.equals(n.path("id").asString())) {
                return n;
            }
        }
        throw new AssertionError("未找到 " + id);
    }
}
