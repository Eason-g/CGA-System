package edu.cga;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import edu.cga.domain.entity.Budget;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * P1 1.1 岗位类型差异化字段：建岗按 jobTypeSchemas 校验必填、下拉选项、经费来源，后端生成派生字段；
 * 配置中心可增删改字段。
 */
class JobTypeSchemaIntegrationTest extends WriteApiTestBase {

    private static final String MENTOR = "zhang.mingyuan";
    private static final String SCHOOL = "zhou.guoqiang";
    private static final String STUDENT = "lin.zhiyuan";

    /** 小规模建岗草稿（1 人 × ¥40 × 4h × 4 周 = ¥640），保证预算与名额校验通过，只测差异化字段 */
    private static Map<String, Object> draft(String type, String budgetId, Map<String, Object> extra) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("type", type);
        d.put("title", "差异化字段测试岗位");
        d.put("deptId", "DEPT_CS");
        d.put("budgetId", budgetId);
        d.put("quota", 1);
        d.put("hourlyRate", 40);
        d.put("weeklyHours", 4);
        d.put("weeks", 4);
        d.put("allowInternational", false);
        d.put("deadline", "2026-12-10");
        d.put("extraFields", extra);
        return d;
    }

    private static Map<String, Object> taExtra() {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("courseCode", "CS3999");
        e.put("courseName", "测试课程");
        e.put("semester", "2026-2027 春季学期");
        e.put("minLangScore", 6.5);
        return e;
    }

    @Test
    void missingRequiredFieldIsRejectedAndBudgetUnchanged() throws Exception {
        Budget before = budget("B_CS_TA");
        Map<String, Object> extra = taExtra();
        extra.remove("courseCode");
        JsonNode r = apiPost("/api/jobs", MENTOR, draft("TA", "B_CS_TA", extra), 400);
        assertThat(r.path("message").asString()).isEqualTo("请填写「绑定课程编号」");

        extra.put("courseCode", "   ");
        apiPost("/api/jobs", MENTOR, draft("TA", "B_CS_TA", extra), 400);
        // 整个 extraFields 缺失也一样拦截
        apiPost("/api/jobs", MENTOR, draft("RA", "B_CS_RA", null), 400);
        assertThat(budget("B_CS_TA").getFrozenAmount()).isEqualByComparingTo(before.getFrozenAmount());
    }

    @Test
    void selectValueOutsideOptionsIsRejected() throws Exception {
        Map<String, Object> extra = taExtra();
        extra.put("minLangScore", 6.8);
        JsonNode r = apiPost("/api/jobs", MENTOR, draft("TA", "B_CS_TA", extra), 400);
        assertThat(r.path("message").asString()).isEqualTo("「语言能力最低要求」的取值不在可选项内");
        extra.put("minLangScore", "abc");
        apiPost("/api/jobs", MENTOR, draft("TA", "B_CS_TA", extra), 400);
    }

    @Test
    void fundSourceMustMatchJobType() throws Exception {
        Budget before = budget("B_CS_RA");
        // TA 岗位使用科研项目经费 → 400
        JsonNode r = apiPost("/api/jobs", MENTOR, draft("TA", "B_CS_RA", taExtra()), 400);
        assertThat(r.path("message").asString()).startsWith("TA 岗位只能使用助教经费（TA_FUND）");
        assertThat(budget("B_CS_RA").getFrozenAmount()).isEqualByComparingTo(before.getFrozenAmount());
        // GA 岗位使用助教经费 → 400（校级管理员也不例外）
        Map<String, Object> ga = draft("GA", "B_CS_TA", Map.of("officeDept", "测试科室", "location", "A101",
                "dutyShift", "周一 9:00-11:00"));
        assertThat(apiPost("/api/jobs", SCHOOL, ga, 400).path("message").asString()).startsWith("GA 岗位只能使用");
    }

    @Test
    void taLanguageRequirementIsDerivedAndUnknownFieldsKept() throws Exception {
        Map<String, Object> extra = taExtra();
        extra.put("minLangScore", 7);
        extra.put("languageRequirement", "前端伪造的文案");
        extra.put("customNote", "未知字段原样保存");
        JsonNode job = apiPost("/api/jobs", MENTOR, draft("TA", "B_CS_TA", extra), 200).path("data");
        JsonNode ef = job.path("extraFields");
        assertThat(ef.path("languageRequirement").asString()).isEqualTo("IELTS 7.0 及以上");
        assertThat(ef.path("customNote").asString()).isEqualTo("未知字段原样保存");
        assertThat(ef.path("courseCode").asString()).isEqualTo("CS3999");

        Map<String, Object> noLang = taExtra();
        noLang.put("minLangScore", 0);
        JsonNode job2 = apiPost("/api/jobs", MENTOR, draft("TA", "B_CS_TA", noLang), 200).path("data");
        assertThat(job2.path("extraFields").path("languageRequirement").asString()).isEqualTo("无特殊语言限制");
        // 落库后再读一遍也一致
        assertThat(jobMapper.selectById(job.path("id").asString()).getExtraFields().get("languageRequirement"))
                .isEqualTo("IELTS 7.0 及以上");
    }

    @Test
    void raMilestonesAreDerivedWithDueDatesSpreadOverTerm() throws Exception {
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("projectCode", "TEST-RA-01");
        extra.put("projectName", "测试课题");
        extra.put("researchDirection", "测试方向");
        extra.put("milestonesText", "M1：调研\n\nM2：原型\r\nM3：总结\n");
        Map<String, Object> d = draft("RA", "B_CS_RA", extra);
        d.put("weeks", 12);
        JsonNode ef = apiPost("/api/jobs", MENTOR, d, 200).path("data").path("extraFields");

        // 截止日 2026-12-10（周四）→ 聘期从 2026-12-14（周一）起，12 周共 84 天，3 条里程碑各占 28 天
        JsonNode ms = ef.path("milestones");
        assertThat(ms.size()).isEqualTo(3);
        assertThat(ms.get(0).path("title").asString()).isEqualTo("M1：调研");
        assertThat(ms.get(0).path("dueDate").asString()).isEqualTo("2027-01-11");
        assertThat(ms.get(1).path("dueDate").asString()).isEqualTo("2027-02-08");
        assertThat(ms.get(2).path("dueDate").asString()).isEqualTo("2027-03-08");
        assertThat(ms.get(2).path("done").asBoolean()).isFalse();
        assertThat(ms.get(0).path("id").asString()).startsWith("MS_");
        assertThat(ef.has("milestonesText")).isFalse();
    }

    @Test
    void configCenterCanAddRequiredCustomField() throws Exception {
        JsonNode schemas = snapshotAs(SCHOOL).path("configs").path("jobTypeSchemas");
        assertThat(schemas.path("TA").path("fields").size()).isEqualTo(5);
        assertThat(schemas.path("RA").path("allowedFundSources").get(0).asString()).isEqualTo("RA_PROJECT");

        @SuppressWarnings("unchecked")
        Map<String, Object> ga = json.convertValue(schemas.path("GA"), Map.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fields = new ArrayList<>((List<Map<String, Object>>) ga.get("fields"));
        fields.add(Map.of("key", "serviceWindow", "label", "服务窗口编号", "type", "select", "required", true,
                "options", List.of(Map.of("value", "W1", "label", "一号窗口"), Map.of("value", "W2", "label", "二号窗口"))));
        Map<String, Object> newGa = new LinkedHashMap<>(ga);
        newGa.put("fields", fields);

        // 非校级管理员不能改配置
        apiPut("/api/configs", MENTOR, Map.of("jobTypeSchemas", Map.of("GA", newGa)), 403);
        JsonNode saved = apiPut("/api/configs", SCHOOL, Map.of("jobTypeSchemas", Map.of("GA", newGa)), 200);
        assertThat(saved.path("data").path("jobTypeSchemas").path("GA").path("fields").size()).isEqualTo(5);
        // 未提交的类型保持原样
        assertThat(saved.path("data").path("jobTypeSchemas").path("TA").path("fields").size()).isEqualTo(5);

        Map<String, Object> gaExtra = new LinkedHashMap<>(Map.of("officeDept", "测试科室", "location", "A101",
                "dutyShift", "周一 9:00-11:00"));
        Map<String, Object> gaDraft = draft("GA", "B_GA_SCHOOL", gaExtra);
        gaDraft.put("deptId", "DEPT_OFFICE");
        assertThat(apiPost("/api/jobs", SCHOOL, gaDraft, 400).path("message").asString())
                .isEqualTo("请填写「服务窗口编号」");
        gaExtra.put("serviceWindow", "W3");
        apiPost("/api/jobs", SCHOOL, gaDraft, 400);
        gaExtra.put("serviceWindow", "W2");
        assertThat(apiPost("/api/jobs", SCHOOL, gaDraft, 200).path("data").path("extraFields")
                .path("serviceWindow").asString()).isEqualTo("W2");

        // 配置校验：不支持的字段类型、派生字段同名、非法经费来源
        Map<String, Object> bad = new LinkedHashMap<>(ga);
        bad.put("fields", List.of(Map.of("key", "x1", "label", "颜色", "type", "color")));
        apiPut("/api/configs", SCHOOL, Map.of("jobTypeSchemas", Map.of("GA", bad)), 400);
        bad.put("fields", List.of(Map.of("key", "milestones", "label", "里程碑", "type", "text")));
        apiPut("/api/configs", SCHOOL, Map.of("jobTypeSchemas", Map.of("GA", bad)), 400);
        bad.put("fields", List.of(Map.of("key", "s1", "label", "下拉", "type", "select", "options", List.of())));
        apiPut("/api/configs", SCHOOL, Map.of("jobTypeSchemas", Map.of("GA", bad)), 400);
        Map<String, Object> badFund = new LinkedHashMap<>(ga);
        badFund.put("allowedFundSources", List.of("BANK_LOAN"));
        apiPut("/api/configs", SCHOOL, Map.of("jobTypeSchemas", Map.of("GA", badFund)), 400);
        apiPut("/api/configs", SCHOOL, Map.of("jobTypeSchemas", Map.of("XA", ga)), 400);

        // 还原
        apiPut("/api/configs", SCHOOL, Map.of("jobTypeSchemas", Map.of("GA", ga)), 200);
        assertThat(snapshotAs(STUDENT).path("configs").path("jobTypeSchemas").path("GA").path("fields").size())
                .isEqualTo(4);
    }
}
