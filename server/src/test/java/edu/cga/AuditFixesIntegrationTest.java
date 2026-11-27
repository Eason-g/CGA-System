package edu.cga;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import edu.cga.domain.entity.Budget;
import edu.cga.domain.entity.Contract;
import edu.cga.domain.entity.Job;
import edu.cga.domain.entity.Payroll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class AuditFixesIntegrationTest extends WriteApiTestBase {

    private static final String SCHOOL = "zhou.guoqiang";
    private static final String MENTOR = "zhang.mingyuan";
    private static final String STUDENT = "lin.zhiyuan";

    @BeforeEach
    void resetSeed() {
        databaseInitializer.rebuild();
    }

    @Test
    void leaveBlocksRenewalSigningAndWork() throws Exception {
        apiPost("/api/integration/sis/sync", SCHOOL, null, 200);
        apiPost("/api/contracts/CON_04/renew", MENTOR, null, 403);
        apiPost("/api/timesheets", "su.yutong",
                Map.of("contractId", "CON_04", "weekNo", 12, "hours", 1, "workLog", "测试"), 403);

        Contract pending = contractMapper.selectById("CON_03");
        var international = userMapper.selectById(pending.getStudentId());
        international.setVisaExpiry(LocalDate.of(2026, 11, 19));
        userMapper.updateById(international);
        apiPost("/api/applications", "alex.chen", Map.of("jobId", "JOB_01"), 403);
        apiPost("/api/contracts/CON_03/sign", "alex.chen",
                Map.of("signatureDataUrl", "data:image/png;base64,iVBORw0KGgo=", "complianceAgreed", true), 403);
    }

    @Test
    void expiredApplicationAndWorkOutsideContractAreRejected() throws Exception {
        Job job = createPublishedJob("T_PAST_DEADLINE", "U_MENTOR", "DEPT_CS", "B_CS_RA", 1, false);
        job.setDeadline(LocalDate.of(2026, 11, 19));
        jobMapper.updateById(job);
        apiPost("/api/applications", STUDENT, Map.of("jobId", job.getId()), 403);

        apiPost("/api/timesheets", STUDENT,
                Map.of("contractId", "CON_01", "weekNo", 1, "hours", 2, "workLog", "聘期前"), 400);
        JsonNode historical = apiPost("/api/timesheets", STUDENT,
                Map.of("contractId", "CON_01", "weekNo", 3, "hours", 2, "workLog", "历史工时"), 200);
        assertThat(historical.path("data").path("month").asString()).isEqualTo("2026-09");
    }

    @Test
    void newJobPreservesFundsPromisedToSubmittedPayroll() throws Exception {
        Budget b = createBudget("T_FUND", "DEPT_CS", "120", "0", "100");
        b.setSourceType("TA_FUND");
        budgetMapper.updateById(b);
        Job j = createPublishedJob("T_RESERVED", "U_MENTOR", "DEPT_CS", b.getId(), 1, false);
        j.setReservedAmount(new BigDecimal("100"));
        jobMapper.updateById(j);
        Contract c = createActiveContract("T_CASH", "U_CN_STU", j);
        createPayroll("T_PROMISED", c, j, "2026-11", "110", "SUBMITTED");

        apiPost("/api/jobs", MENTOR, taDraft(b.getId(), 20, false), 400);
        assertThat(budget(b.getId()).getFrozenAmount()).isEqualByComparingTo("100");
        apiPost("/api/jobs", MENTOR, taDraft(b.getId(), 10, false), 200);
        assertThat(budget(b.getId()).getFrozenAmount()).isEqualByComparingTo("110");
    }

    @Test
    void approvedHoursAfterPaymentCreateExactlyOneSupplement() throws Exception {
        apiPost("/api/payrolls/generate", SCHOOL, Map.of("month", "2026-11"), 200);
        Payroll first = payrollMapper.selectBatches("CON_01", "2026-11").get(0);
        apiPost("/api/payrolls/" + first.getId() + "/payout", SCHOOL, null, 200);
        apiPost("/api/timesheets/TS_03/review", MENTOR, Map.of("decision", "APPROVE"), 200);

        apiPost("/api/payrolls/generate", SCHOOL, Map.of("month", "2026-11"), 200);
        apiPost("/api/payrolls/generate", SCHOOL, Map.of("month", "2026-11"), 200);
        List<Payroll> batches = payrollMapper.selectBatches("CON_01", "2026-11");
        assertThat(batches).hasSize(2);
        Payroll supplement = batches.stream().filter(p -> "PENDING_PAY".equals(p.getFinanceStatus())).findFirst().orElseThrow();
        assertThat(supplement.getTotalHours()).isEqualByComparingTo("11");
        assertThat(supplement.getAmount()).isEqualByComparingTo("580.80");

        apiPost("/api/payrolls/" + supplement.getId() + "/payout", SCHOOL, null, 200);
        apiPost("/api/payrolls/generate", SCHOOL, Map.of("month", "2026-11"), 200);
        assertThat(payrollMapper.selectBatches("CON_01", "2026-11")).hasSize(2).allMatch(p -> "PAID".equals(p.getFinanceStatus()));
    }

    @Test
    void aLegacyPublishedJobWithoutReviewCannotAcceptInternationalStudents() throws Exception {
        Job job = createPublishedJob("T_UNREVIEWED", "U_MENTOR", "DEPT_CS", "B_CS_TA", 1, true);
        job.setWorkflowSteps(List.of("MENTOR_SUBMIT", "DEPT_REVIEW", "PUBLISHED"));
        job.setCurrentStepIndex(2);
        jobMapper.updateById(job);

        apiPost("/api/applications", "alex.chen", Map.of("jobId", job.getId()), 403);
        apiPost("/api/applications", STUDENT, Map.of("jobId", job.getId()), 200);
    }

    @Test
    void configuredWorkflowStillRequiresInternationalReview() throws Exception {
        apiPut("/api/configs", SCHOOL, Map.of("workflows", Map.of("TA", List.of(
                Map.of("key", "MENTOR_SUBMIT"), Map.of("key", "DEPT_REVIEW"), Map.of("key", "PUBLISHED")))), 200);
        JsonNode draft = apiPost("/api/jobs", MENTOR, taDraft("B_CS_TA", 10, true), 200).path("data");
        String jobId = draft.path("id").asString();

        JsonNode review = apiPost("/api/jobs/" + jobId + "/review", "li.wanqing", Map.of("decision", "APPROVE"), 200);
        assertThat(review.path("data").path("status").asString()).isEqualTo("PENDING_INTL_REVIEW");
        apiPost("/api/jobs/" + jobId + "/review", "li.wanqing", Map.of("decision", "APPROVE"), 403);
        JsonNode published = apiPost("/api/jobs/" + jobId + "/review", "wang.haitao", Map.of("decision", "APPROVE"), 200);
        assertThat(published.path("data").path("status").asString()).isEqualTo("PUBLISHED");
    }

    private Map<String, Object> taDraft(String budgetId, int rate, boolean international) {
        return Map.of("type", "TA", "title", "复审测试岗位", "deptId", "DEPT_CS",
                "budgetId", budgetId, "quota", 1, "hourlyRate", rate, "weeklyHours", 1, "weeks", 1,
                "allowInternational", international,
                "extraFields", Map.of("courseCode", "T-REVIEW", "courseName", "复审", "semester", "测试学期"));
    }
}

