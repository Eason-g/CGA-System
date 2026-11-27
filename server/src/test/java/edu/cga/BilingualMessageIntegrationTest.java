package edu.cga;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import edu.cga.domain.entity.Budget;
import edu.cga.domain.entity.Contract;
import edu.cga.domain.entity.Job;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import tools.jackson.databind.JsonNode;

/**
 * 后端双语（docs/双语设计.md 第 3 节）：请求头 Accept-Language 为英文时，被合规规则拦截的响应带 messageEn；
 * 不带头或 zh-CN 时响应与原来一致（message 为中文、没有 messageEn）；checks[] 不论语言都带 labelEn、detailEn。
 */
class BilingualMessageIntegrationTest extends WriteApiTestBase {

    private static final String MENTOR = "zhang.mingyuan";
    private static final String EN = "en-US";
    private static final String ZH = "zh-CN";

    /** 以某账号身份 POST，可指定 Accept-Language（null 表示不带头），断言状态码并返回响应体 */
    private JsonNode post(String url, String account, Object body, String lang, int expectedStatus) throws Exception {
        var req = MockMvcRequestBuilders.post(url);
        if (account != null) {
            req.header("Authorization", bearer(tokenOf(account)));
        }
        if (lang != null) {
            req.header("Accept-Language", lang);
        }
        if (body != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        var resp = mvc.perform(req).andReturn().getResponse();
        String text = resp.getContentAsString(StandardCharsets.UTF_8);
        assertThat(resp.getStatus()).as("POST %s（%s, %s）→ %s", url, account, lang, text).isEqualTo(expectedStatus);
        return json.readTree(text);
    }

    @Test
    void hoursOverLimitApplicationInEnglish() throws Exception {
        createStudent("T_EN_INTL", "DEPT_CS", true, true);
        Job other = createPublishedJob("T_EN_J0", "U_MENTOR", "DEPT_CS", "B_CS_RA", 12, true);
        createActiveContract("T_EN_C0", "T_EN_INTL", other);
        createPublishedJob("T_EN_J1", "U_MENTOR", "DEPT_CS", "B_CS_RA", 14, true);
        Map<String, Object> body = Map.of("jobId", "T_EN_J1", "statement", "test");

        JsonNode en = post("/api/applications", "t_en_intl", body, EN, 403);
        assertThat(en.path("message").asString()).isEqualTo("合规前置校验未通过，系统已拦截提交");
        assertThat(en.path("messageEn").asString())
                .isEqualTo("Pre-application compliance checks failed; the submission has been blocked");
        JsonNode hours = findCheck(en.path("data").path("checks"), "hours");
        assertThat(hours.path("labelEn").asString()).isEqualTo("Cross-job weekly hours check");
        assertThat(hours.path("detailEn").asString())
                .isEqualTo("Currently allocated 12h + this position 14h = 26h > limit 20h/week (over by 6h)");
        assertThat(hours.path("detail").asString()).isEqualTo("当前已占 12h + 本岗 14h = 26h > 上限 20h/周（超出 6h）");
        assertThat(findCheck(en.path("data").path("checks"), "visa").path("detailEn").asString())
                .isEqualTo("X1学习居留许可 valid (expiry date: 2027-07-31)");

        // 不带头、zh-CN：message 不变、不出现 messageEn；checks 仍带英文
        for (String lang : new String[] {null, ZH}) {
            JsonNode zh = post("/api/applications", "t_en_intl", body, lang, 403);
            assertThat(zh.path("message").asString()).isEqualTo("合规前置校验未通过，系统已拦截提交");
            assertThat(zh.has("messageEn")).as("lang=%s", lang).isFalse();
            assertThat(findCheck(zh.path("data").path("checks"), "hours").path("detailEn").asString())
                    .isEqualTo(hours.path("detailEn").asString());
        }
    }

    @Test
    void invalidVisaSpecialApprovalInEnglish() throws Exception {
        createStudent("T_EN_NOVISA", "DEPT_CS", true, false);
        createPublishedJob("T_EN_J2", "U_MENTOR", "DEPT_CS", "B_CS_RA", 8, true);
        Map<String, Object> body = Map.of("jobId", "T_EN_J2", "statement", "test",
                "isSpecialApproval", true, "specialReason", "visa renewal in progress");
        JsonNode r = post("/api/applications", "t_en_novisa", body, EN, 403);
        assertThat(r.path("message").asString())
                .isEqualTo("不能发起特批：签证类型校验（有效学习居留许可）未通过，属于硬性条件，不可特批");
        assertThat(r.path("messageEn").asString()).isEqualTo("Cannot request special approval: Not passed: "
                + "Visa type check (valid study residence permit) (mandatory requirement, special approval not allowed)");
        assertThat(r.path("data").path("specialBlockedReasonEn").asString()).startsWith("Not passed: ");
        assertThat(findCheck(r.path("data").path("checks"), "visa").path("detailEn").asString()).isEqualTo(
                "No valid study residence permit found; on-campus teaching/research assistant work is not permitted");

        JsonNode zh = post("/api/applications", "t_en_novisa", body, ZH, 403);
        assertThat(zh.path("message").asString()).isEqualTo(r.path("message").asString());
        assertThat(zh.has("messageEn")).isFalse();
    }

    @Test
    void hireOverLimitInEnglish() throws Exception {
        createStudent("T_EN_MULTI", "DEPT_CS", false, true);
        List<String> appIds = new ArrayList<>();
        for (String id : List.of("T_EN_H1", "T_EN_H2", "T_EN_H3")) {
            createPublishedJob(id, "U_MENTOR", "DEPT_CS", "B_CS_RA", 8, true);
            appIds.add(post("/api/applications", "t_en_multi", Map.of("jobId", id), null, 200)
                    .path("data").path("id").asString());
        }
        Map<String, Object> hire = Map.of("score", 90, "decision", "HIRE", "comment", "");
        post("/api/applications/" + appIds.get(0) + "/decision", MENTOR, hire, EN, 200);
        post("/api/applications/" + appIds.get(1) + "/decision", MENTOR, hire, EN, 200);
        JsonNode third = post("/api/applications/" + appIds.get(2) + "/decision", MENTOR, hire, EN, 403);
        assertThat(third.path("message").asString())
                .isEqualTo("录用后跨岗累计周工时将达 24h（已占 16h + 本岗 8h），超出上限 20h/周，系统已拦截录用");
        String expectedEn = "After hiring, cross-job weekly hours would reach 24h (allocated 16h + this position 8h), "
                + "exceeding the limit of 20h/week; hiring has been blocked";
        assertThat(third.path("messageEn").asString()).isEqualTo(expectedEn);
        assertThat(third.path("data").path("errorsEn").get(0).asString()).isEqualTo(expectedEn);
        assertThat(third.path("data").path("errorsEn").size()).isEqualTo(third.path("data").path("errors").size());
    }

    @Test
    void timesheetOverLimitInEnglish() throws Exception {
        createStudent("T_EN_TS", "DEPT_CS", false, true);
        Job job = createPublishedJob("T_EN_TSJ", "U_MENTOR", "DEPT_CS", "B_CS_RA", 10, false);
        Contract c = createActiveContract("T_EN_TSC", "T_EN_TS", job);
        createApprovedTimesheet("T_EN_TS1", c, 12, "2026-11", "15");
        Map<String, Object> body = Map.of("contractId", c.getId(), "weekNo", 12, "hours", 8, "workLog", "test");

        JsonNode en = post("/api/timesheets", "t_en_ts", body, EN, 403);
        assertThat(en.path("message").asString())
                .isEqualTo("第 12 周跨岗累计工时将达 23h（已报 15h + 本次 8h），超出学校红线 20h/周，系统已拦截提交！");
        assertThat(en.path("messageEn").asString()).isEqualTo("Cross-job hours for week 12 would reach 23h "
                + "(already reported 15h + this entry 8h), exceeding the university limit of 20h/week; submission has been blocked!");
        assertThat(post("/api/timesheets", "t_en_ts", body, null, 403).has("messageEn")).isFalse();
    }

    @Test
    void jobOverBudgetInEnglish() throws Exception {
        Budget b = createBudget("T_EN_FUND", "DEPT_CS", "100", "0", "0");
        b.setSourceType("TA_FUND");
        budgetMapper.updateById(b);
        Map<String, Object> draft = Map.of("type", "TA", "title", "budget test", "deptId", "DEPT_CS",
                "budgetId", b.getId(), "quota", 1, "hourlyRate", 50, "weeklyHours", 2, "weeks", 2,
                "allowInternational", false,
                "extraFields", Map.of("courseCode", "T-EN", "courseName", "EN", "semester", "test"));
        JsonNode r = post("/api/jobs", MENTOR, draft, EN, 400);
        // 种子数据里计算机学院名额已用满，同时触发名额拦截
        assertThat(r.path("message").asString()).isEqualTo("预计总支出 ¥200 超出可用预算 ¥100，系统已拦截发布；"
                + "招聘人数 1 人超出院系剩余名额 0 人，系统已拦截发布");
        assertThat(r.path("messageEn").asString())
                .isEqualTo("Estimated total cost ¥200 exceeds the available budget by ¥100; publishing has been blocked; "
                        + "Headcount of 1 exceeds the department's remaining quota of 0; publishing has been blocked");
        assertThat(r.path("data").path("errorsEn").get(0).asString())
                .isEqualTo("Estimated total cost ¥200 exceeds the available budget by ¥100; publishing has been blocked");
    }

    @Test
    void loginFailureInEnglish() throws Exception {
        Map<String, Object> body = Map.of("account", "nobody.bilingual", "password", "wrong-password");
        JsonNode en = post("/api/auth/login", null, body, "en-US,en;q=0.9,zh-CN;q=0.8", 401);
        assertThat(en.path("message").asString()).isEqualTo("账号或密码错误");
        assertThat(en.path("messageEn").asString()).isEqualTo("Incorrect account or password");
        JsonNode zh = post("/api/auth/login", null, body, "zh-CN,zh;q=0.9,en;q=0.8", 401);
        assertThat(zh.path("message").asString()).isEqualTo("账号或密码错误");
        assertThat(zh.has("messageEn")).isFalse();
    }

    @Test
    void snapshotDepartmentsHaveEnglishNames() throws Exception {
        JsonNode depts = snapshotAs("alex.chen").path("departments");
        assertThat(depts.size()).isEqualTo(4);
        JsonNode cs = null;
        for (JsonNode d : depts) {
            assertThat(d.path("nameEn").asString()).isNotBlank();
            assertThat(d.path("shortNameEn").asString()).isNotBlank();
            if ("DEPT_CS".equals(d.path("id").asString())) {
                cs = d;
            }
        }
        assertThat(cs).isNotNull();
        assertThat(cs.path("nameEn").asString()).isEqualTo("School of Computer Science and Technology");
        assertThat(cs.path("shortNameEn").asString()).isEqualTo("Computer Science");
        assertThat(cs.path("name").asString()).isEqualTo("计算机科学与技术学院");
    }

    /** 推荐条目的 reasonsEn、blockersEn 与 reasons、blockers 一一对应；因子带 labelEn、detailEn */
    @Test
    void recommendationsHaveEnglishReasonsAndBlockers() throws Exception {
        var resp = mvc.perform(MockMvcRequestBuilders.get("/api/recommendations")
                        .header("Authorization", bearer(tokenOf("alex.chen"))))
                .andReturn().getResponse();
        assertThat(resp.getStatus()).isEqualTo(200);
        JsonNode items = json.readTree(resp.getContentAsString(StandardCharsets.UTF_8)).path("data").path("items");
        assertThat(items.size()).isPositive();
        Map<String, JsonNode> byId = new java.util.HashMap<>();
        for (JsonNode it : items) {
            byId.put(it.path("jobId").asString(), it);
            assertThat(it.path("reasonsEn").size()).as(it.path("jobId").asString()).isEqualTo(it.path("reasons").size());
            assertThat(it.path("blockersEn").size()).as(it.path("jobId").asString()).isEqualTo(it.path("blockers").size());
            for (JsonNode f : it.path("factors")) {
                assertThat(f.path("labelEn").asString()).isNotBlank();
                assertThat(f.path("detailEn").asString()).isNotBlank();
            }
        }
        JsonNode job04 = byId.get("JOB_04");
        assertThat(job04.path("blockers").get(0).asString()).startsWith("岗位国际生开放资格校验未通过：");
        assertThat(job04.path("blockersEn").get(0).asString()).isEqualTo(
                "Position open to international students not passed: "
                        + "This position is not open to international students (Chinese students only)");
        JsonNode job02 = byId.get("JOB_02");
        assertThat(job02.path("blockersEn").get(0).asString()).isEqualTo(
                "Cross-job weekly hours would exceed the limit (24h > 20h); International Office special approval required");
        assertThat(byId.get("JOB_03").path("blockersEn").toString())
                .contains("Language requirement not met; special approval required: ");
        assertThat(byId.get("JOB_01").path("reasonsEn").toString()).contains("Skill match 3/3: ");
    }

    /** 老库（无 name_en、short_name_en 列）启动时自动补列并按种子回填英文名 */
    @Test
    void legacyDatabaseGetsDepartmentEnglishColumns() throws Exception {
        var jdbc = new org.springframework.jdbc.core.JdbcTemplate(dataSource);
        try {
            jdbc.execute("ALTER TABLE departments DROP COLUMN short_name_en");
            jdbc.execute("ALTER TABLE departments DROP COLUMN name_en");
            databaseInitializer.ensureDepartmentEnglishColumns();
            databaseInitializer.ensureDepartmentEnglishColumns(); // 幂等
            assertThat(jdbc.queryForObject("SELECT name_en FROM departments WHERE id = 'DEPT_MATH'", String.class))
                    .isEqualTo("School of Mathematical Sciences");
            assertThat(jdbc.queryForObject("SELECT short_name_en FROM departments WHERE id = 'DEPT_ECON'", String.class))
                    .isEqualTo("Economics & Management");
        } finally {
            databaseInitializer.rebuild();
        }
    }

    @org.springframework.beans.factory.annotation.Autowired
    private javax.sql.DataSource dataSource;

    private static JsonNode findCheck(JsonNode checks, String key) {
        for (JsonNode c : checks) {
            if (key.equals(c.path("key").asString())) {
                return c;
            }
        }
        throw new AssertionError("缺少校验项 " + key + "：" + checks);
    }
}
