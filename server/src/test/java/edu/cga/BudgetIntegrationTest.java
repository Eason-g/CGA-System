package edu.cga;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import edu.cga.domain.entity.AuditLog;
import edu.cga.domain.entity.Budget;
import edu.cga.domain.entity.Contract;
import edu.cga.domain.entity.Job;
import edu.cga.domain.entity.Payroll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import tools.jackson.databind.JsonNode;

/**
 * 设计文档第 9 节第 4、5 条：建岗超预算/超名额拦截且冻结额不变；薪酬核算超预算拦截；发放后 used/frozen 变化正确。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BudgetIntegrationTest extends WriteApiTestBase {

    private static final String MENTOR = "zhang.mingyuan";
    private static final String SCHOOL = "zhou.guoqiang";

    private static Map<String, Object> draft(String budgetId, int quota, int rate, int weeklyHours, int weeks) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("type", "RA");
        d.put("title", "测试建岗");
        d.put("deptId", "DEPT_CS");
        d.put("budgetId", budgetId);
        d.put("quota", quota);
        d.put("hourlyRate", rate);
        d.put("weeklyHours", weeklyHours);
        d.put("weeks", weeks);
        d.put("allowInternational", false);
        return d;
    }

    // ---------------------------------------------------------------- 第 4 条：建岗

    /** 建岗用例先跑：后面的用例会构造计算机学院的测试岗位，占用院系名额 */
    @Test
    @Order(1)
    void createJobOverBudgetIsBlockedAndFrozenUnchanged() throws Exception {
        Budget before = budget("B_CS_RA_TIGHT"); // 18000 − 15800 − 1000 = 可用 1200
        long jobsBefore = jobMapper.selectCount(null);
        // 1 × ¥50 × 8h × 16 周 = ¥6,400 > ¥1,200
        JsonNode r = apiPost("/api/jobs", MENTOR, draft("B_CS_RA_TIGHT", 1, 50, 8, 16), 400);
        assertThat(r.path("message").asString()).isEqualTo("预计总支出 ¥6,400 超出可用预算 ¥5,200，系统已拦截发布");
        assertThat(r.path("data").path("budgetExceeded").asBoolean()).isTrue();
        assertThat(r.path("data").path("availableBudget").decimalValue()).isEqualByComparingTo("1200");

        assertThat(budget("B_CS_RA_TIGHT").getFrozenAmount()).isEqualByComparingTo(before.getFrozenAmount());
        assertThat(jobMapper.selectCount(null)).isEqualTo(jobsBefore);
    }

    @Test
    @Order(2)
    void createJobOverDeptQuotaIsBlockedAndFrozenUnchanged() throws Exception {
        Budget before = budget("B_CS_TA");
        // 计算机学院上限 22；种子数据中未驳回岗位名额合计 15（本类其他用例可能再建岗，按库中实际值计算）
        int used = jobMapper.selectByDept("DEPT_CS").stream()
                .filter(j -> !"REJECTED".equals(j.getStatus())).mapToInt(Job::getQuota).sum();
        int remaining = Math.max(0, 22 - used);
        JsonNode r = apiPost("/api/jobs", MENTOR, draft("B_CS_TA", remaining + 1, 1, 1, 1), 400);
        assertThat(r.path("message").asString())
                .isEqualTo("招聘人数 " + (remaining + 1) + " 人超出院系剩余名额 " + remaining + " 人，系统已拦截发布");
        assertThat(r.path("data").path("quotaInfo").path("remaining").asInt()).isEqualTo(remaining);
        assertThat(budget("B_CS_TA").getFrozenAmount()).isEqualByComparingTo(before.getFrozenAmount());
    }

    @Test
    void rejectingJobReleasesReservedAmount() throws Exception {
        Budget before = budget("B_ECON_RA");
        JsonNode created = apiPost("/api/jobs", "zhao.minghua", Map.of("type", "RA", "title", "测试驳回", "deptId", "DEPT_ECON",
                "budgetId", "B_ECON_RA", "quota", 1, "hourlyRate", 40, "weeklyHours", 5, "weeks", 10,
                // P1：RA 岗位的必填差异化字段（jobTypeSchemas）
                "extraFields", Map.of("projectCode", "TEST-01", "projectName", "测试课题", "researchDirection", "测试方向")),
                200);
        String jobId = created.path("data").path("id").asString();
        assertThat(budget("B_ECON_RA").getFrozenAmount())
                .isEqualByComparingTo(before.getFrozenAmount().add(new BigDecimal("2000")));

        // 经管学院没有院系管理员账号，由校级管理员代审驳回
        JsonNode rej = apiPost("/api/jobs/" + jobId + "/review", SCHOOL, Map.of("decision", "REJECT"), 200);
        assertThat(rej.path("message").asString()).isEqualTo("已驳回该岗位申请");
        assertThat(rej.path("data").path("status").asString()).isEqualTo("REJECTED");
        assertThat(rej.path("data").path("reservedAmount").decimalValue()).isEqualByComparingTo("0");
        assertThat(budget("B_ECON_RA").getFrozenAmount()).isEqualByComparingTo(before.getFrozenAmount());
    }

    // ---------------------------------------------------------------- 第 5 条：核算与发放

    @Test
    void payrollOverBudgetIsBlocked() throws Exception {
        // 经费可用 100，岗位预留 0；10h × ¥40 = ¥400 > 0 + 100
        createBudget("T_B_LOW", "DEPT_CS", "1000", "900", "0");
        Job job = createPublishedJob("T_J_LOW", "U_MENTOR", "DEPT_CS", "T_B_LOW", 10, false);
        Contract con = createActiveContract("T_CON_LOW", "U_STU_SU", job);
        createApprovedTimesheet("T_TS_LOW", con, 11, "2026-11", "10");

        JsonNode r = apiPost("/api/payrolls/generate", SCHOOL, Map.of("month", "2026-11"), 200);
        // data 只有 { count, blocked } 两项
        assertThat(r.path("data").propertyNames()).containsExactly("count", "blocked");
        // 本类其他用例构造的数据也可能被拦截，这里只找本用例的那一笔
        JsonNode blocked = r.path("data").path("blocked");
        JsonNode mine = null;
        for (JsonNode b : blocked) {
            if ("T_CON_LOW".equals(b.path("contractId").asString())) {
                mine = b;
            }
        }
        assertThat(mine).isNotNull();
        assertThat(mine.path("studentId").asString()).isEqualTo("U_STU_SU");
        assertThat(mine.path("amount").decimalValue()).isEqualByComparingTo("400");
        assertThat(mine.path("reason").asString())
                .isEqualTo("核算金额 ¥400 超出岗位剩余预留 ¥0 与经费可用额 ¥100 之和");
        int count = r.path("data").path("count").asInt();
        assertThat(count).isPositive(); // 种子数据中的其他合同照常核算
        assertThat(r.path("message").asString())
                .isEqualTo("已完成 2026-11 薪酬自动核算（更新 " + count + " 笔，预算拦截 " + blocked.size() + " 笔）");

        // 被拦截的不生成薪酬单，经费不变，审计日志写明
        assertThat(payrollMapper.selectByContractAndMonth("T_CON_LOW", "2026-11")).isNull();
        assertThat(budget("T_B_LOW").getUsedAmount()).isEqualByComparingTo("900");
        // 同一秒内可能有多条核算日志，按内容查找本次核算写下的那条
        java.util.List<AuditLog> logs = auditLogMapper.selectList(new QueryWrapper<AuditLog>()
                .eq("target_type", "PAYROLL").eq("target_id", "BATCH_2026-11").like("opinion", "T_CON_LOW"));
        assertThat(logs).isNotEmpty();
        assertThat(logs.get(0).getOpinion()).contains("已拦截未生成").contains("T_CON_LOW（¥400）");
        assertThat(logs.get(0).getAfterValue()).contains("预算拦截");
    }

    @Test
    void payoutUpdatesUsedAndReleasesOnlyThisJobsReserve() throws Exception {
        // PAY_05：苏雨桐 JOB_01（有剩余预留，尚无已发放），金额 ¥396
        Payroll pay = payrollMapper.selectById("PAY_05");
        Budget before = budget("B_CS_TA");
        long auditBefore = auditCount();

        JsonNode r = apiPost("/api/payrolls/PAY_05/payout", SCHOOL, null, 200);
        assertThat(r.path("data").path("financeStatus").asString()).isEqualTo("PAID");
        Budget after = budget("B_CS_TA");
        assertThat(after.getUsedAmount()).isEqualByComparingTo(before.getUsedAmount().add(pay.getAmount()));
        assertThat(after.getFrozenAmount()).isEqualByComparingTo(before.getFrozenAmount().subtract(pay.getAmount()));
        assertThat(auditCount()).isEqualTo(auditBefore + 1);

        // 重复发放被拦截
        JsonNode again = apiPost("/api/payrolls/PAY_05/payout", SCHOOL, null, 400);
        assertThat(again.path("message").asString()).isEqualTo("该笔薪酬已发放，请勿重复打款");
        // 未核算（PENDING_CALC）的不能发放
        createBudget("T_B_CALC", "DEPT_CS", "10000", "0", "0");
        Job job = createPublishedJob("T_J_CALC", "U_MENTOR", "DEPT_CS", "T_B_CALC", 4, false);
        Contract con = createActiveContract("T_CON_CALC", "U_STU_SU", job);
        createPayroll("T_PAY_CALC", con, job, "2026-10", "100", "PENDING_CALC");
        JsonNode calc = apiPost("/api/payrolls/T_PAY_CALC/payout", SCHOOL, null, 400);
        assertThat(calc.path("message").asString()).isEqualTo("该笔薪酬尚未核算，请先执行月度核算");
    }

    @Test
    void payoutBeyondJobReserveDoesNotTouchOtherJobsReserve() throws Exception {
        // 岗位预留 300，已发放 0；本笔 ¥500：冻结只扣 300，其余 200 走可用额
        createBudget("T_B_PAY", "DEPT_CS", "10000", "0", "5300"); // 其他岗位预留 5000 + 本岗位 300
        Job job = createPublishedJob("T_J_PAY", "U_MENTOR", "DEPT_CS", "T_B_PAY", 10, false);
        job.setReservedAmount(new BigDecimal("300"));
        jobMapper.updateById(job);
        Contract con = createActiveContract("T_CON_PAY", "U_STU_SU", job);
        createPayroll("T_PAY_1", con, job, "2026-10", "500", "PENDING_PAY");

        apiPost("/api/payrolls/T_PAY_1/payout", SCHOOL, null, 200);
        Budget after = budget("T_B_PAY");
        assertThat(after.getUsedAmount()).isEqualByComparingTo("500");
        assertThat(after.getFrozenAmount()).isEqualByComparingTo("5000");
    }

    @Test
    void pendingExcessOfOtherPayrollsCountsAgainstAvailable() throws Exception {
        // 两个岗位各预留 200（冻结共 400），经费可用 700 − 0 − 400 = 300。
        // 每笔核算 10h × ¥40 = ¥400，超出本岗位预留 200。第一笔：400 ≤ 200 + 300，通过；
        // 第二笔：可用额要先让出第一笔的 200 超额，400 > 200 + 100，必须拦截（否则两笔合计透支 100）。
        createBudget("T_B_EXC", "DEPT_CS", "700", "0", "400");
        Contract[] cons = new Contract[2];
        String[] suffix = {"A", "B"};
        for (int i = 0; i < 2; i++) {
            Job job = createPublishedJob("T_J_EX" + suffix[i], "U_MENTOR", "DEPT_CS", "T_B_EXC", 10, false);
            job.setReservedAmount(new BigDecimal("200"));
            jobMapper.updateById(job);
            cons[i] = createActiveContract("T_CON_EX" + suffix[i], "U_STU_SU", job);
            createApprovedTimesheet("T_TS_EX" + suffix[i], cons[i], 11, "2026-11", "10");
        }

        JsonNode r = apiPost("/api/payrolls/generate", SCHOOL, Map.of("month", "2026-11"), 200);
        String blocked = r.path("data").path("blocked").toString();
        assertThat(blocked).doesNotContain("T_CON_EXA").contains("T_CON_EXB");
        assertThat(payrollMapper.selectByContractAndMonth("T_CON_EXA", "2026-11")).isNotNull();
        assertThat(payrollMapper.selectByContractAndMonth("T_CON_EXB", "2026-11")).isNull();
    }

    @Test
    void evaluationKeepsOriginalAmountWhenNewCoefficientExceedsBudget() throws Exception {
        // 岗位预留 400、经费可用 0；待发放 ¥400（10h × ¥40 × 1.0），考核优秀 1.1 → ¥440 超预算
        createBudget("T_B_EV", "DEPT_CS", "1000", "600", "400");
        Job job = createPublishedJob("T_J_EV", "U_MENTOR", "DEPT_CS", "T_B_EV", 10, false);
        job.setReservedAmount(new BigDecimal("400"));
        jobMapper.updateById(job);
        Contract con = createActiveContract("T_CON_EV", "U_STU_SU", job);
        createApprovedTimesheet("T_TS_EV", con, 11, "2026-11", "10");
        JsonNode gen = apiPost("/api/payrolls/generate", SCHOOL, Map.of("month", "2026-11"), 200);
        assertThat(gen.path("data").path("blocked").toString()).doesNotContain("T_CON_EV");
        Payroll pending = payrollMapper.selectByContractAndMonth("T_CON_EV", "2026-11");
        assertThat(pending.getAmount()).isEqualByComparingTo("400");

        JsonNode ev = apiPost("/api/evaluations", MENTOR, Map.of("contractId", "T_CON_EV", "period", "2026-11 阶段考核",
                "dimensionScores", Map.of("mentor", 95, "service", 92), "comment", "表现优秀"), 200);
        assertThat(ev.path("data").path("grade").asString()).isEqualTo("优秀");
        assertThat(ev.path("data").path("totalScore").decimalValue()).isEqualByComparingTo("94.3");
        assertThat(ev.path("message").asString()).startsWith("考核已提交：优秀（94.3分，系数 1.1x）；关联未发放薪酬 ")
                .contains("已保留原金额 ¥400.00");
        Payroll kept = payrollMapper.selectById(pending.getId());
        assertThat(kept.getAmount()).isEqualByComparingTo("400");
        assertThat(kept.getCoefficient()).isEqualByComparingTo("1");

        // 预算充足时正常更新系数：PAY_03（林致远 JOB_04，24h × ¥48）
        JsonNode ok = apiPost("/api/evaluations", MENTOR, Map.of("contractId", "CON_01",
                "dimensionScores", Map.of("mentor", 70, "service", 70)), 200);
        assertThat(ok.path("message").asString()).isEqualTo("考核已提交：合格（70分，系数 1x）");
        assertThat(ok.path("data").path("period").asString()).isEqualTo("2026-11 周期考核");
        Payroll pay03 = payrollMapper.selectById("PAY_03");
        assertThat(pay03.getAmount()).isEqualByComparingTo("1152");
    }

    @Test
    void saveConfigValidation() throws Exception {
        JsonNode w = apiPut("/api/configs", SCHOOL, Map.of("evaluationTemplates", Map.of("TA", java.util.List.of(
                Map.of("key", "mentor", "label", "导师评价", "weight", 60),
                Map.of("key", "service", "label", "评教", "weight", 30)))), 400);
        assertThat(w.path("message").asString()).isEqualTo("TA 考核评价维度权重之和必须等于 100%（当前 90%）");
        JsonNode h = apiPut("/api/configs", SCHOOL, Map.of("globalRules", Map.of("maxWeeklyHoursDefault", 41)), 400);
        assertThat(h.path("message").asString()).isEqualTo("周工时上限必须为 1–40 的整数");
        apiPut("/api/configs", SCHOOL, Map.of("globalRules", Map.of("maxWeeklyHoursDefault", 12.5)), 400);
        apiPut("/api/configs", SCHOOL, Map.of("workflows", Map.of("TA", java.util.List.of(Map.of("key", "UNKNOWN")))), 400);
        // 都没有写入
        JsonNode snap = snapshotAs(SCHOOL);
        assertThat(snap.path("configs").path("globalRules").path("maxWeeklyHoursDefault").asInt()).isEqualTo(20);
        assertThat(snap.path("configs").path("evaluationTemplates").path("TA").get(0).path("weight").asInt()).isEqualTo(70);
    }
}
