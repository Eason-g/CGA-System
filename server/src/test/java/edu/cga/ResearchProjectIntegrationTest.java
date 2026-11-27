package edu.cga;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;

import edu.cga.domain.entity.Budget;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import tools.jackson.databind.JsonNode;

/**
 * P2 B.3 科研系统对接：RA 建岗时项目不存在 / 负责人不符 / 项目先于聘期结束各 400 且不冻结经费，
 * 正常建岗通过，管理员代建只要求同院系；发布页“校验项目”接口。
 */
class ResearchProjectIntegrationTest extends WriteApiTestBase {

    private static final String MENTOR = "zhang.mingyuan";
    private static final String DEPT_ADMIN = "li.wanqing";

    /** 1 人 × ¥40 × 4h × 12 周；截止 2026-12-10 → 聘期 2026-12-14 起，预计 2027-03-08 结束 */
    private static Map<String, Object> raDraft(String projectCode) {
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("projectCode", projectCode);
        extra.put("projectName", "测试课题");
        extra.put("researchDirection", "测试方向");
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("type", "RA");
        d.put("title", "科研项目校验测试岗位");
        d.put("deptId", "DEPT_CS");
        d.put("budgetId", "B_CS_RA");
        d.put("quota", 1);
        d.put("hourlyRate", 40);
        d.put("weeklyHours", 4);
        d.put("weeks", 12);
        d.put("allowInternational", false);
        d.put("deadline", "2026-12-10");
        d.put("extraFields", extra);
        return d;
    }

    private void assertRejected(String account, String code, String expectedMessage) throws Exception {
        Budget before = budget("B_CS_RA");
        long jobs = jobMapper.selectCount(null);
        JsonNode r = apiPost("/api/jobs", account, raDraft(code), 400);
        assertThat(r.path("message").asString()).isEqualTo(expectedMessage);
        assertThat(r.path("data").path("ok").asBoolean()).isFalse();
        assertThat(budget("B_CS_RA").getFrozenAmount()).isEqualByComparingTo(before.getFrozenAmount());
        assertThat(jobMapper.selectCount(null)).isEqualTo(jobs);
    }

    @Test
    void projectLookupDoesNotExposeOtherDepartments() throws Exception {
        send(org.springframework.http.HttpMethod.GET,
                "/api/integration/research/projects/KY-2026-MA01", MENTOR, null, 404);
        send(org.springframework.http.HttpMethod.GET,
                "/api/integration/research/projects/KY-2026-MA01?deptId=DEPT_MATH", DEPT_ADMIN, null, 404);
    }

    @Test
    void unknownProjectIs400() throws Exception {
        assertRejected(MENTOR, "NSFC-00000000", "科研系统中不存在该项目或项目不在可用范围");
    }

    @Test
    void leaderMismatchIs400() throws Exception {
        assertRejected(MENTOR, "KY-2026-EC02",
                "科研系统中不存在该项目或项目不在可用范围");
        // 院系管理员代建：负责人须属于岗位所在院系
        assertRejected(DEPT_ADMIN, "KY-2026-EC02",
                "科研系统中不存在该项目或项目不在可用范围");
    }

    @Test
    void projectEndingBeforeTermIs400() throws Exception {
        assertRejected(MENTOR, "KY-2025-CS09",
                "项目「KY-2025-CS09」结束日 2027-01-15 早于岗位预计聘期结束日 2027-03-08，请缩短聘期或更换项目");
    }

    @Test
    void validProjectPasses() throws Exception {
        JsonNode job = apiPost("/api/jobs", MENTOR, raDraft("NSFC-62572018"), 200).path("data");
        assertThat(job.path("extraFields").path("projectCode").asString()).isEqualTo("NSFC-62572018");
        // 院系管理员代建：负责人张明远属于计算机学院 → 通过
        apiPost("/api/jobs", DEPT_ADMIN, raDraft("NSFC-62572018"), 200);
    }

    @Test
    void lookupEndpointShowsProjectAndChecks() throws Exception {
        JsonNode r = send(HttpMethod.GET,
                "/api/integration/research/projects/NSFC-62572018?deptId=DEPT_CS&deadline=2026-12-10&weeks=12",
                MENTOR, null, 200);
        JsonNode d = r.path("data");
        assertThat(d.path("ok").asBoolean()).isTrue();
        assertThat(d.path("source").asString()).isEqualTo("模拟科研系统");
        assertThat(d.path("project").path("name").asString()).isEqualTo("面向异构集群的大模型低延迟推理优化");
        assertThat(d.path("project").path("endDate").asString()).isEqualTo("2028-08-31");
        assertThat(d.path("jobEndDate").asString()).isEqualTo("2027-03-08");
        assertThat(d.path("checks").size()).isEqualTo(3);

        JsonNode end = send(HttpMethod.GET,
                "/api/integration/research/projects/KY-2025-CS09?deadline=2026-12-10&weeks=12", MENTOR, null, 200);
        assertThat(end.path("data").path("ok").asBoolean()).isFalse();
        assertThat(end.path("message").asString()).contains("早于岗位预计聘期结束日");

        send(HttpMethod.GET, "/api/integration/research/projects/NOPE-1", MENTOR, null, 404);
        send(HttpMethod.GET, "/api/integration/research/projects/NSFC-62572018", "lin.zhiyuan", null, 403);
    }
}
