package edu.cga;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.http.HttpMethod;
import tools.jackson.databind.JsonNode;

/**
 * P1 1.2 岗位模板：可见范围（全校通用 + 本院系）、保存权限、删除权限（创建者或校级管理员），以及快照中的 jobTemplates。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class JobTemplateIntegrationTest extends WriteApiTestBase {

    private static final String MENTOR_CS = "zhang.mingyuan";
    private static final String DEPT_ADMIN_CS = "li.wanqing";
    private static final String MENTOR_MATH = "chen.shouren";
    private static final String SCHOOL = "zhou.guoqiang";
    private static final String STUDENT = "lin.zhiyuan";
    private static final String INTL = "wang.haitao";

    private static Map<String, Object> payload() {
        return Map.of("title", "模板里不应保存岗位名称", "quota", 1, "hourlyRate", 50, "weeklyHours", 6, "weeks", 12,
                "extraFields", Map.of("projectCode", "TPL-01"));
    }

    private JsonNode list(String account) throws Exception {
        return send(HttpMethod.GET, "/api/job-templates", account, null, 200).path("data");
    }

    private static List<String> ids(JsonNode arr) {
        List<String> ids = new ArrayList<>();
        arr.forEach(n -> ids.add(n.path("id").asString()));
        return ids;
    }

    private String create(String account, Map<String, Object> body) throws Exception {
        return apiPost("/api/job-templates", account, body, 200).path("data").path("id").asString();
    }

    @Test
    @Order(1)
    void visibilityIsSchoolWidePlusOwnDepartment() throws Exception {
        // 种子：每种类型各一个全校通用模板
        assertThat(ids(list(MENTOR_CS))).contains("JT_TA_01", "JT_RA_01", "JT_GA_01");
        // 全校通用模板按 dept_id 升序（NULL 在前）、创建时间倒序
        assertThat(ids(list(MENTOR_MATH))).containsExactly("JT_GA_01", "JT_RA_01", "JT_TA_01");

        JsonNode created = apiPost("/api/job-templates", MENTOR_CS,
                Map.of("name", "  计算机学院助研模板  ", "type", "RA", "payload", payload()), 200).path("data");
        String csId = created.path("id").asString();
        assertThat(created.path("name").asString()).isEqualTo("计算机学院助研模板");
        assertThat(created.path("deptId").asString()).isEqualTo("DEPT_CS");
        assertThat(created.path("createdBy").asString()).isEqualTo("U_MENTOR");
        assertThat(created.path("payload").has("title")).isFalse();
        assertThat(created.path("payload").path("extraFields").path("projectCode").asString()).isEqualTo("TPL-01");

        // 本院系可见（院系管理员也能看到），外院不可见
        assertThat(ids(list(MENTOR_CS))).contains(csId);
        assertThat(ids(list(DEPT_ADMIN_CS))).contains(csId);
        assertThat(ids(list(MENTOR_MATH))).doesNotContain(csId);
        assertThat(ids(snapshotAs(MENTOR_MATH).path("jobTemplates"))).doesNotContain(csId).contains("JT_TA_01");
        assertThat(ids(snapshotAs(MENTOR_CS).path("jobTemplates"))).contains(csId, "JT_TA_01");
        assertThat(ids(snapshotAs(SCHOOL).path("jobTemplates"))).contains(csId);
        // 学生快照里没有模板
        assertThat(snapshotAs(STUDENT).path("jobTemplates").size()).isZero();

        // 校级管理员存全校通用模板：外院导师也能看到
        JsonNode global = apiPost("/api/job-templates", SCHOOL,
                Map.of("name", "全校通用助管", "type", "GA", "global", true, "payload", payload()), 200).path("data");
        assertThat(global.path("deptId").isNull()).isTrue();
        assertThat(ids(list(MENTOR_MATH))).contains(global.path("id").asString());
    }

    @Test
    @Order(2)
    void savePermissionsAndValidation() throws Exception {
        // 导师、院系管理员不能存全校通用模板
        JsonNode r = apiPost("/api/job-templates", MENTOR_CS,
                Map.of("name", "想存全校通用", "type", "TA", "global", true, "payload", payload()), 403);
        assertThat(r.path("message").asString()).isEqualTo("只有校级管理员可以保存全校通用模板");
        apiPost("/api/job-templates", DEPT_ADMIN_CS,
                Map.of("name", "想存全校通用", "type", "TA", "global", true, "payload", payload()), 403);
        // 院系管理员存本院系模板：导师传的 deptId 被忽略
        JsonNode dept = apiPost("/api/job-templates", DEPT_ADMIN_CS,
                Map.of("name", "院系模板", "type", "TA", "deptId", "DEPT_MATH", "payload", payload()), 200).path("data");
        assertThat(dept.path("deptId").asString()).isEqualTo("DEPT_CS");
        // 校级管理员可指定院系
        JsonNode math = apiPost("/api/job-templates", SCHOOL,
                Map.of("name", "数学学院模板", "type", "TA", "deptId", "DEPT_MATH", "payload", payload()), 200).path("data");
        assertThat(math.path("deptId").asString()).isEqualTo("DEPT_MATH");
        assertThat(ids(list(MENTOR_MATH))).contains(math.path("id").asString());

        // 学生、国际生办无权访问
        apiPost("/api/job-templates", STUDENT, Map.of("name", "x", "type", "TA", "payload", payload()), 403);
        send(HttpMethod.GET, "/api/job-templates", STUDENT, null, 403);
        send(HttpMethod.GET, "/api/job-templates", INTL, null, 403);
        send(HttpMethod.GET, "/api/job-templates", null, null, 401);

        // 参数校验
        apiPost("/api/job-templates", MENTOR_CS, Map.of("name", " ", "type", "TA", "payload", payload()), 400);
        apiPost("/api/job-templates", MENTOR_CS, Map.of("name", "x", "type", "XA", "payload", payload()), 400);
        apiPost("/api/job-templates", MENTOR_CS, Map.of("name", "x", "type", "TA"), 400);
    }

    @Test
    @Order(3)
    void onlyCreatorOrSchoolAdminCanDelete() throws Exception {
        String mine = create(MENTOR_CS, Map.of("name", "导师自己的模板", "type", "TA", "payload", payload()));

        // 同院系的院系管理员不是创建者 → 403；外院导师看不到 → 404
        JsonNode r = send(HttpMethod.DELETE, "/api/job-templates/" + mine, DEPT_ADMIN_CS, null, 403);
        assertThat(r.path("message").asString()).isEqualTo("只能删除自己创建的模板");
        send(HttpMethod.DELETE, "/api/job-templates/" + mine, MENTOR_MATH, null, 404);
        send(HttpMethod.DELETE, "/api/job-templates/" + mine, STUDENT, null, 403);
        // 导师不能删校级管理员建的全校通用模板
        send(HttpMethod.DELETE, "/api/job-templates/JT_TA_01", MENTOR_CS, null, 403);

        long logs = auditCount();
        send(HttpMethod.DELETE, "/api/job-templates/" + mine, MENTOR_CS, null, 200);
        assertThat(ids(list(MENTOR_CS))).doesNotContain(mine);
        assertThat(auditCount()).isEqualTo(logs + 1);
        send(HttpMethod.DELETE, "/api/job-templates/" + mine, MENTOR_CS, null, 404);

        // 校级管理员可删除任何模板（包括别人建的院系模板）
        String other = create(DEPT_ADMIN_CS, Map.of("name", "院系管理员的模板", "type", "GA", "payload", payload()));
        send(HttpMethod.DELETE, "/api/job-templates/" + other, SCHOOL, null, 200);
        send(HttpMethod.DELETE, "/api/job-templates/JT_GA_01", SCHOOL, null, 200);
        assertThat(ids(list(MENTOR_CS))).doesNotContain("JT_GA_01");
    }
}
