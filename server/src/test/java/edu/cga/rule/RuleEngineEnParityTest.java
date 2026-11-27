package edu.cga.rule;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import edu.cga.domain.entity.Budget;
import edu.cga.domain.entity.Contract;
import edu.cga.domain.entity.Department;
import edu.cga.domain.entity.Job;
import edu.cga.domain.entity.JobApplication;
import edu.cga.domain.entity.Timesheet;
import edu.cga.domain.entity.User;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Java 与 JS 规则引擎中英文案逐字一致（docs/双语设计.md 第 3 节）。
 * <p>
 * 期望值 rule-en-parity.json 由 {@code node scripts/gen-rule-en-parity.mjs} 调用 src/services/ruleEngine.js 生成；
 * 这里用同样的输入调用 Java RuleEngine，比较 label/labelEn/detail/detailEn、errors/errorsEn、message/messageEn。
 * 改了任一端的文案后：先重新生成 JSON（或用 --check 核对 JS），再跑本测试核对 Java。
 */
class RuleEngineEnParityTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final JsonNode FIXTURE = load();

    private static JsonNode load() {
        try (InputStream in = RuleEngineEnParityTest.class.getResourceAsStream("/rule-en-parity.json")) {
            return JSON.readTree(in);
        } catch (Exception e) {
            throw new IllegalStateException("读取 rule-en-parity.json 失败", e);
        }
    }

    static Stream<Arguments> cases() {
        List<Arguments> out = new ArrayList<>();
        FIXTURE.path("cases").forEach(c -> out.add(Arguments.of(c.path("name").asString(), c)));
        return out.stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void javaMatchesJs(String name, JsonNode c) {
        LocalDate today = LocalDate.parse(FIXTURE.path("today").asString());
        Map<String, Object> rules = toMap(FIXTURE.path("globalRules"));
        JsonNode in = c.path("input");
        JsonNode expected = c.path("expected");
        Map<String, Object> actual = new LinkedHashMap<>();
        switch (c.path("fn").asString()) {
            case "application" -> {
                RuleEngine.ApplicationCheck r = RuleEngine.validateStudentApplication(user(in.path("student")),
                        job(in.path("job")), applications(in.path("applications")), contracts(in.path("contracts")),
                        rules, today);
                actual.put("passed", r.passed());
                actual.put("canSpecialApprove", r.canSpecialApprove());
                actual.put("checks", r.checks().stream().map(RuleEngineEnParityTest::check).toList());
                actual.put("hints", r.hints().stream().map(RuleEngineEnParityTest::check).toList());
                actual.put("specialBlockedReason", r.specialBlockedReason());
                actual.put("specialBlockedReasonEn", r.specialBlockedReasonEn());
            }
            case "hire" -> {
                Job job = job(in.path("job"));
                LocalDate end = RuleEngine.predictContractEnd(today, job.getWeeks());
                RuleEngine.HireCheck r = RuleEngine.validateHire(application(in.path("app")), job,
                        user(in.path("student")), contracts(in.path("contracts")), rules, end);
                actual.put("ok", r.ok());
                actual.put("hoursOverridden", r.hoursOverridden());
                actual.put("errors", r.errors());
                actual.put("errorsEn", r.errorsEn());
            }
            case "timesheet" -> {
                RuleEngine.TimesheetCheck r = RuleEngine.validateTimesheetSubmission(user(in.path("student")),
                        in.path("weekNo").asInt(), decimal(in.path("hours")), timesheets(in.path("timesheets")), rules);
                actual.put("ok", r.ok());
                actual.put("message", r.message());
                actual.put("messageEn", r.messageEn());
            }
            case "jobPublish" -> {
                JsonNode f = in.path("form");
                RuleEngine.JobForm form = new RuleEngine.JobForm(f.path("deptId").asString(), f.path("budgetId").asString(),
                        f.path("quota").asInt(), decimal(f.path("hourlyRate")), f.path("weeklyHours").asInt(),
                        f.path("weeks").asInt());
                RuleEngine.JobPublishCheck r = RuleEngine.validateJobPublish(form, budget(in.path("budget")),
                        dept(in.path("dept")), jobs(in.path("jobs")));
                actual.put("ok", r.ok());
                actual.put("errors", r.errors());
                actual.put("errorsEn", r.errorsEn());
            }
            case "eligibility" -> {
                User s = user(in.path("student"));
                LocalDate through = LocalDate.parse(in.path("through").asString());
                actual.put("zh", RuleEngine.checkWorkingEligibility(s, through));
                var en = RuleEngine.workingEligibilityText(s, through);
                actual.put("en", en == null ? null : en.en());
                actual.put("enrollment", RuleEngine.checkEnrollment(s));
                var enrollment = RuleEngine.enrollmentText(s);
                actual.put("enrollmentEn", enrollment == null ? null : enrollment.en());
            }
            default -> throw new IllegalArgumentException("未知用例类型 " + c.path("fn").asString());
        }
        JsonNode actualTree = JSON.valueToTree(actual);
        assertThat(actualTree.toString()).as(name).isEqualTo(expected.toString());
    }

    // ---------------------------------------------------------------- JSON → 实体

    private static Map<String, Object> check(RuleEngine.Check c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", c.key());
        m.put("label", c.label());
        m.put("labelEn", c.labelEn());
        m.put("passed", c.passed());
        m.put("detail", c.detail());
        m.put("detailEn", c.detailEn());
        return m;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toMap(JsonNode n) {
        return n == null || n.isNull() || n.isMissingNode() ? null : JSON.convertValue(n, LinkedHashMap.class);
    }

    private static String str(JsonNode n) {
        return n == null || n.isNull() || n.isMissingNode() ? null : n.asString();
    }

    private static LocalDate date(JsonNode n) {
        String s = str(n);
        return s == null ? null : LocalDate.parse(s);
    }

    private static BigDecimal decimal(JsonNode n) {
        return n == null || n.isNull() || n.isMissingNode() ? null : n.decimalValue();
    }

    private static Boolean bool(JsonNode n) {
        return n == null || n.isNull() || n.isMissingNode() ? null : n.asBoolean();
    }

    private static Integer integer(JsonNode n) {
        return n == null || n.isNull() || n.isMissingNode() ? null : n.asInt();
    }

    private static User user(JsonNode n) {
        User u = new User();
        u.setId(str(n.path("id")));
        u.setRole("STUDENT");
        u.setIsInternational(bool(n.path("isInternational")));
        u.setVisaType(str(n.path("visaType")));
        u.setVisaValid(bool(n.path("visaValid")));
        u.setVisaExpiry(date(n.path("visaExpiry")));
        u.setLanguageCert(str(n.path("languageCert")));
        u.setLanguageLevel(decimal(n.path("languageLevel")));
        u.setLanguageValid(bool(n.path("languageValid")));
        u.setLanguageCertStatus(str(n.path("languageCertStatus")));
        u.setMaxWeeklyHours(integer(n.path("maxWeeklyHours")));
        u.setEnrollmentStatus(str(n.path("enrollmentStatus")));
        return u;
    }

    private static Job job(JsonNode n) {
        Job j = new Job();
        j.setId(str(n.path("id")));
        j.setDeptId(str(n.path("deptId")));
        j.setQuota(integer(n.path("quota")));
        j.setHiredCount(integer(n.path("hiredCount")));
        j.setWeeklyHours(integer(n.path("weeklyHours")));
        j.setWeeks(integer(n.path("weeks")));
        j.setAllowInternational(bool(n.path("allowInternational")));
        List<String> steps = new ArrayList<>();
        n.path("workflowSteps").forEach(s -> steps.add(s.asString()));
        j.setWorkflowSteps(steps);
        j.setCurrentStepIndex(integer(n.path("currentStepIndex")));
        j.setDeadline(date(n.path("deadline")));
        j.setExtraFields(toMap(n.path("extraFields")));
        j.setStatus(n.has("status") ? str(n.path("status")) : "PUBLISHED");
        return j;
    }

    private static List<Job> jobs(JsonNode arr) {
        List<Job> out = new ArrayList<>();
        arr.forEach(n -> out.add(job(n)));
        return out;
    }

    private static List<Contract> contracts(JsonNode arr) {
        List<Contract> out = new ArrayList<>();
        arr.forEach(n -> {
            Contract c = new Contract();
            c.setStudentId(str(n.path("studentId")));
            c.setStatus(str(n.path("status")));
            c.setStartDate(date(n.path("startDate")));
            c.setEndDate(date(n.path("endDate")));
            c.setWeeklyHours(integer(n.path("weeklyHours")));
            out.add(c);
        });
        return out;
    }

    private static JobApplication application(JsonNode n) {
        JobApplication a = new JobApplication();
        a.setStudentId(str(n.path("studentId")));
        a.setJobId(str(n.path("jobId")));
        a.setStatus(str(n.path("status")));
        a.setSpecialStatus(str(n.path("specialStatus")));
        a.setComplianceCheckResult(toMap(n.path("complianceCheckResult")));
        return a;
    }

    private static List<JobApplication> applications(JsonNode arr) {
        List<JobApplication> out = new ArrayList<>();
        arr.forEach(n -> out.add(application(n)));
        return out;
    }

    private static List<Timesheet> timesheets(JsonNode arr) {
        List<Timesheet> out = new ArrayList<>();
        arr.forEach(n -> {
            Timesheet t = new Timesheet();
            t.setStudentId(str(n.path("studentId")));
            t.setWeekNo(integer(n.path("weekNo")));
            t.setHours(decimal(n.path("hours")));
            t.setStatus(str(n.path("status")));
            out.add(t);
        });
        return out;
    }

    private static Budget budget(JsonNode n) {
        if (n == null || n.isNull() || n.isMissingNode()) {
            return null;
        }
        Budget b = new Budget();
        b.setId(str(n.path("id")));
        b.setTotalAmount(decimal(n.path("totalAmount")));
        b.setUsedAmount(decimal(n.path("usedAmount")));
        b.setFrozenAmount(decimal(n.path("frozenAmount")));
        return b;
    }

    private static Department dept(JsonNode n) {
        Department d = new Department();
        d.setId(str(n.path("id")));
        d.setName(str(n.path("name")));
        d.setQuotaLimit(integer(n.path("quotaLimit")));
        return d;
    }
}
