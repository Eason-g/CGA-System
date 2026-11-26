package edu.cga;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import edu.cga.domain.entity.Contract;
import edu.cga.domain.entity.Job;
import edu.cga.domain.entity.JobApplication;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * 设计文档第 9 节第 3 条：三个已修漏洞（对应 scripts/verify-p0-fixes.mjs）在后端同样被拦截。
 */
class P0FixesIntegrationTest extends WriteApiTestBase {

    private static final String MENTOR = "zhang.mingyuan";
    private static final String SCHOOL = "zhou.guoqiang";

    /** 漏洞 1：录用时复核跨岗累计工时，连续录用第三个 8h 岗位被拦截 */
    @Test
    void thirdConsecutiveHireIsBlocked() throws Exception {
        createStudent("T_MULTI", "DEPT_CS", false, true);
        List<Job> jobs = List.of(
                createPublishedJob("T_J1", "U_MENTOR", "DEPT_CS", "B_CS_RA", 8, true),
                createPublishedJob("T_J2", "U_MENTOR", "DEPT_CS", "B_CS_RA", 8, true),
                createPublishedJob("T_J3", "U_MENTOR", "DEPT_CS", "B_CS_RA", 8, true));

        // 三个 8h 岗位单独申请都能通过（申请时合同工时为 0）
        List<String> appIds = new ArrayList<>();
        for (Job j : jobs) {
            JsonNode r = apiPost("/api/applications", "t_multi", Map.of("jobId", j.getId(), "statement", "测试"), 200);
            assertThat(r.path("data").path("status").asString()).isEqualTo("SUBMITTED");
            appIds.add(r.path("data").path("id").asString());
        }

        Map<String, Object> hire = Map.of("score", 90, "decision", "HIRE", "comment", "");
        apiPost("/api/applications/" + appIds.get(0) + "/decision", MENTOR, hire, 200);
        apiPost("/api/applications/" + appIds.get(1) + "/decision", MENTOR, hire, 200);
        JsonNode third = apiPost("/api/applications/" + appIds.get(2) + "/decision", MENTOR, hire, 403);
        assertThat(third.path("message").asString())
                .isEqualTo("录用后跨岗累计周工时将达 24h（已占 16h + 本岗 8h），超出上限 20h/周，系统已拦截录用");
        assertThat(third.path("data").path("ok").asBoolean()).isFalse();
        assertThat(third.path("data").path("projectedHours").asInt()).isEqualTo(24);

        // 合同累计周工时没有超过 20h；被拦截的申请没有留下评分或状态变化
        int allocated = contractMapper.selectAllocatingByStudent("T_MULTI").stream()
                .mapToInt(Contract::getWeeklyHours).sum();
        assertThat(allocated).isEqualTo(16);
        JobApplication blocked = applicationMapper.selectById(appIds.get(2));
        assertThat(blocked.getStatus()).isEqualTo("SUBMITTED");
        assertThat(blocked.getScore()).isNull();
        assertThat(jobMapper.selectById("T_J3").getHiredCount()).isZero();
    }

    /** 漏洞 2：签证无效的国际生不能发起特批（硬性条件不可特批） */
    @Test
    void invalidVisaCannotRequestSpecialApproval() throws Exception {
        createStudent("T_NOVISA", "DEPT_CS", true, false);
        createPublishedJob("T_J4", "U_MENTOR", "DEPT_CS", "B_CS_RA", 8, true);
        JsonNode r = apiPost("/api/applications", "t_novisa", Map.of("jobId", "T_J4", "statement", "测试",
                "isSpecialApproval", true, "specialReason", "签证续办中，申请先行上岗",
                // 伪造学籍字段也没用：后端以库中学籍为准
                "student", Map.of("visaValid", true)), 403);
        assertThat(r.path("message").asString())
                .isEqualTo("不能发起特批：签证类型校验（有效学习居留许可）未通过，属于硬性条件，不可特批");
        assertThat(r.path("data").path("canSpecialApprove").asBoolean()).isFalse();
        assertThat(applicationMapper.selectCount(new QueryWrapper<JobApplication>().eq("student_id", "T_NOVISA")))
                .isZero();

        // 签证有效、仅工时超限时仍可发起特批（原有功能不受影响）
        createStudent("T_SOFT", "DEPT_CS", true, true);
        createPublishedJob("T_J5", "U_MENTOR", "DEPT_CS", "B_CS_RA", 24, true);
        JsonNode ok = apiPost("/api/applications", "t_soft", Map.of("jobId", "T_J5", "statement", "测试",
                "isSpecialApproval", true, "specialReason", "寒假集中排班，学期内平均不超标"), 200);
        assertThat(ok.path("data").path("status").asString()).isEqualTo("PENDING_SPECIAL_APPROVAL");
        assertThat(ok.path("message").asString()).isEqualTo("已提交特批申请，等待国际生办审核留痕");

        // 特批理由不足 6 字
        createPublishedJob("T_J5B", "U_MENTOR", "DEPT_CS", "B_CS_RA", 24, true);
        JsonNode shortReason = apiPost("/api/applications", "t_soft", Map.of("jobId", "T_J5B",
                "isSpecialApproval", true, "specialReason", "太短"), 400);
        assertThat(shortReason.path("message").asString()).isEqualTo("发起特批申请必须填写不少于 6 个字的特批理由");
    }

    /** 漏洞 3：国际生上限改为 12h 后申请 14h 岗位被拦截，国内生通过 */
    @Test
    void internationalCapFromConfigCenter() throws Exception {
        JsonNode cfg = apiPut("/api/configs", SCHOOL, Map.of("globalRules", Map.of("maxWeeklyHoursIntl", 12)), 200);
        assertThat(cfg.path("data").path("globalRules").path("maxWeeklyHoursIntl").asInt()).isEqualTo(12);
        // 合并保存：其他规则保持原值
        assertThat(cfg.path("data").path("globalRules").path("maxWeeklyHoursDefault").asInt()).isEqualTo(20);
        try {
            createStudent("T_INTL", "DEPT_CS", true, true);
            createStudent("T_DOM", "DEPT_CS", false, true);
            createPublishedJob("T_J6", "U_MENTOR", "DEPT_CS", "B_CS_RA", 14, true);

            JsonNode intl = apiPost("/api/applications", "t_intl", Map.of("jobId", "T_J6", "statement", "测试"), 403);
            assertThat(intl.path("message").asString()).isEqualTo("合规前置校验未通过，系统已拦截提交");
            JsonNode hours = null;
            for (JsonNode c : intl.path("data").path("checks")) {
                if ("hours".equals(c.path("key").asString())) {
                    hours = c;
                }
            }
            assertThat(hours).isNotNull();
            assertThat(hours.path("passed").asBoolean()).isFalse();
            assertThat(hours.path("detail").asString()).isEqualTo("当前已占 0h + 本岗 14h = 14h > 上限 12h/周（超出 2h）");
            assertThat(intl.path("data").path("maxHours").asInt()).isEqualTo(12);

            JsonNode dom = apiPost("/api/applications", "t_dom", Map.of("jobId", "T_J6", "statement", "测试"), 200);
            assertThat(dom.path("data").path("complianceCheckResult").path("maxHours").asInt()).isEqualTo(20);
        } finally {
            apiPut("/api/configs", SCHOOL, Map.of("globalRules", Map.of("maxWeeklyHoursIntl", 20)), 200);
        }
    }

    /** 特批通过后，录用复核以特批时记录的 projectedHours 为上限 */
    @Test
    void specialApprovalThenHireWithinApprovedCeiling() throws Exception {
        createStudent("T_SPEC", "DEPT_CS", true, true);
        createPublishedJob("T_J7", "U_MENTOR", "DEPT_CS", "B_CS_RA", 22, true);
        JsonNode app = apiPost("/api/applications", "t_spec", Map.of("jobId", "T_J7",
                "isSpecialApproval", true, "specialReason", "寒假集中排班，学期内平均不超标"), 200);
        String appId = app.path("data").path("id").asString();

        // 导师不能审特批；国际生办通过
        apiPost("/api/applications/" + appId + "/special-review", MENTOR, Map.of("decision", "APPROVE"), 403);
        JsonNode sr = apiPost("/api/applications/" + appId + "/special-review", "wang.haitao",
                Map.of("decision", "APPROVE"), 200);
        assertThat(sr.path("data").path("status").asString()).isEqualTo("SPECIAL_APPROVED");
        assertThat(sr.path("message").asString()).isEqualTo("特批已通过并流转至导师选拔");
        apiPost("/api/applications/" + appId + "/special-review", "wang.haitao", Map.of("decision", "APPROVE"), 400);

        JsonNode hired = apiPost("/api/applications/" + appId + "/decision", MENTOR,
                Map.of("score", 88, "decision", "HIRE"), 200);
        assertThat(hired.path("data").path("status").asString()).isEqualTo("HIRED");
    }
}
