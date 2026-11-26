package edu.cga;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Map;

import edu.cga.domain.entity.Contract;
import edu.cga.domain.entity.Job;
import edu.cga.domain.entity.User;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * P1 1.4 续聘：考核合格时生成新的待签合同（开始日 = 原合同结束日次日，周数同岗位），原合同不变；
 * 考核不合格 / 跨岗工时超限 / 签证有效期不覆盖新聘期 → 403；权限反例。
 */
class RenewalIntegrationTest extends WriteApiTestBase {

    private static final String MENTOR = "zhang.mingyuan";
    private static final String MENTOR_MATH = "chen.shouren";
    private static final String DEPT_ADMIN = "li.wanqing";
    private static final String SCHOOL = "zhou.guoqiang";
    private static final String INTL = "wang.haitao";

    private void evaluate(String contractId, int score) throws Exception {
        apiPost("/api/evaluations", MENTOR, Map.of("contractId", contractId,
                "dimensionScores", Map.of("mentor", score, "service", score)), 200);
    }

    @Test
    void renewEligibleContractCreatesPendingContract() throws Exception {
        // CON_01：林致远 · JOB_04（RA，18 周，12h/周）· 2026-09-14 至 2027-01-18；种子考核 EV_01 优秀、可续聘
        Contract before = contractMapper.selectById("CON_01");
        Job job = jobMapper.selectById("JOB_04");

        // 权限反例：学生、院系管理员、国际生办 403；外院导师 404；未登录 401
        apiPost("/api/contracts/CON_01/renew", "lin.zhiyuan", null, 403);
        apiPost("/api/contracts/CON_01/renew", DEPT_ADMIN, null, 403);
        apiPost("/api/contracts/CON_01/renew", INTL, null, 403);
        apiPost("/api/contracts/CON_01/renew", MENTOR_MATH, null, 404);
        apiPost("/api/contracts/CON_01/renew", null, null, 401);

        long logs = auditCount();
        JsonNode created = apiPost("/api/contracts/CON_01/renew", MENTOR, null, 200).path("data");
        LocalDate expectedStart = before.getEndDate().plusDays(1);
        assertThat(created.path("startDate").asString()).isEqualTo(expectedStart.toString()).isEqualTo("2027-01-19");
        assertThat(created.path("endDate").asString()).isEqualTo(expectedStart.plusWeeks(job.getWeeks()).toString());
        assertThat(created.path("status").asString()).isEqualTo("PENDING_SIGN");
        assertThat(created.path("studentId").asString()).isEqualTo("U_CN_STU");
        assertThat(created.path("jobId").asString()).isEqualTo("JOB_04");
        assertThat(created.path("weeklyHours").asInt()).isEqualTo(job.getWeeklyHours());
        assertThat(created.path("id").asString()).isNotEqualTo("CON_01");
        assertThat(auditCount()).isEqualTo(logs + 1);

        // 原合同不变
        Contract after = contractMapper.selectById("CON_01");
        assertThat(after.getStatus()).isEqualTo("ACTIVE");
        assertThat(after.getEndDate()).isEqualTo(before.getEndDate());
        // 学生能在快照里看到新的待签合同
        JsonNode snap = snapshotAs("lin.zhiyuan");
        boolean found = false;
        for (JsonNode c : snap.path("contracts")) {
            found |= created.path("id").asString().equals(c.path("id").asString());
        }
        assertThat(found).isTrue();

        // 不能重复续聘
        apiPost("/api/contracts/CON_01/renew", MENTOR, null, 400);
        // 新合同还没生效，不能对它续聘
        apiPost("/api/contracts/" + created.path("id").asString() + "/renew", MENTOR, null, 400);
    }

    @Test
    void failedEvaluationBlocksRenewal() throws Exception {
        // CON_04：最近一次考核改为不合格
        evaluate("CON_04", 50);
        JsonNode r = apiPost("/api/contracts/CON_04/renew", MENTOR, null, 403);
        assertThat(r.path("message").asString()).contains("不合格").contains("不具备续聘资格");
        assertThat(r.path("data").path("ok").asBoolean()).isFalse();
        // 没有考核记录也不能续聘（CON_05 属于数学学院，用校级管理员）
        JsonNode none = apiPost("/api/contracts/CON_05/renew", SCHOOL, null, 403);
        assertThat(none.path("message").asString()).contains("还没有周期考核记录");
    }

    @Test
    void crossJobHoursOverLimitBlocksRenewal() throws Exception {
        User s = createStudent("T_RN_HOURS", "DEPT_CS", false, false);
        Job a = createPublishedJob("T_JOB_RN_A", "U_MENTOR", "DEPT_CS", "B_CS_RA", 12, false);
        Job b = createPublishedJob("T_JOB_RN_B", "U_MENTOR", "DEPT_CS", "B_CS_RA", 10, false);
        Contract ca = createActiveContract("T_CON_RN_A", s.getId(), a); // 2026-11-02 至 2027-02-22
        Contract cb = createActiveContract("T_CON_RN_B", s.getId(), b);
        evaluate(ca.getId(), 95);

        // B 合同延长到覆盖 A 的新聘期（2027-02-23 起）：同期 10h + 本岗 12h = 22h > 20h
        cb.setEndDate(LocalDate.of(2027, 6, 30));
        contractMapper.updateById(cb);
        JsonNode r = apiPost("/api/contracts/" + ca.getId() + "/renew", MENTOR, null, 403);
        assertThat(r.path("message").asString()).contains("跨岗累计周工时将达 22h").contains("超出上限 20h/周");
        assertThat(r.path("data").path("projectedHours").asInt()).isEqualTo(22);

        // B 合同在新聘期开始前结束 → 不计入，续聘通过
        cb.setEndDate(ca.getEndDate());
        contractMapper.updateById(cb);
        apiPost("/api/contracts/" + ca.getId() + "/renew", MENTOR, null, 200);
    }

    @Test
    void visaMustCoverNewTerm() throws Exception {
        // CON_02：Alex（国际生）· JOB_10（RA，16 周）· 至 2026-12-01 → 新聘期 2026-12-02 至 2027-03-24
        evaluate("CON_02", 92);
        User alex = userMapper.selectById("U_INTL_STU");
        LocalDate original = alex.getVisaExpiry();
        alex.setVisaExpiry(LocalDate.of(2027, 3, 1));
        userMapper.updateById(alex);
        long contractsBefore = contractMapper.selectCount(null);
        try {
            JsonNode r = apiPost("/api/contracts/CON_02/renew", MENTOR, null, 403);
            assertThat(r.path("message").asString())
                    .contains("有效期至 2027-03-01").contains("未覆盖新聘期（2026-12-02 至 2027-03-24）");
            assertThat(contractMapper.selectCount(null)).isEqualTo(contractsBefore);

            // 签证覆盖新聘期 → 续聘通过（校级管理员也可发起）
            alex.setVisaExpiry(original);
            userMapper.updateById(alex);
            JsonNode ok = apiPost("/api/contracts/CON_02/renew", SCHOOL, null, 200).path("data");
            assertThat(ok.path("startDate").asString()).isEqualTo("2026-12-02");
            assertThat(ok.path("endDate").asString()).isEqualTo("2027-03-24");
        } finally {
            alex.setVisaExpiry(original);
            userMapper.updateById(alex);
        }
    }
}
