package edu.cga.rule;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import edu.cga.domain.entity.Budget;
import edu.cga.domain.entity.Contract;
import edu.cga.domain.entity.Department;
import edu.cga.domain.entity.Job;
import edu.cga.domain.entity.JobApplication;
import edu.cga.domain.entity.Timesheet;
import edu.cga.domain.entity.User;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 规则引擎单元测试（不连库）：每个函数覆盖通过与拦截分支，并核对与 ruleEngine.js 一致的中文文案。
 */
class RuleEngineTest {

    // ---------------------------------------------------------------- 测试数据构造

    private static Map<String, Object> rules(int def, int intl) {
        Map<String, Object> m = new HashMap<>();
        m.put("maxWeeklyHoursDefault", def);
        m.put("maxWeeklyHoursIntl", intl);
        return m;
    }

    private static User student(String id, boolean intl) {
        User u = new User();
        u.setId(id);
        u.setName(id);
        u.setRole("STUDENT");
        u.setIsInternational(intl);
        u.setVisaType(intl ? "X1学习居留许可" : null);
        u.setVisaValid(true);
        u.setVisaExpiry(intl ? LocalDate.of(2027, 7, 31) : null);
        u.setLanguageCert("IELTS 7.0");
        u.setLanguageLevel(new BigDecimal("7.0"));
        u.setLanguageValid(true);
        u.setMaxWeeklyHours(20);
        return u;
    }

    private static Job job(String id, int weeklyHours) {
        Job j = new Job();
        j.setId(id);
        j.setDeptId("DEPT_CS");
        j.setQuota(2);
        j.setHiredCount(0);
        j.setWeeklyHours(weeklyHours);
        j.setHourlyRate(new BigDecimal("45"));
        j.setWeeks(16);
        j.setAllowInternational(true);
        j.setStatus("PUBLISHED");
        j.setExtraFields(new LinkedHashMap<>());
        return j;
    }

    private static Contract contract(String studentId, int hours, String status) {
        Contract c = new Contract();
        c.setId("CON_" + hours + status);
        c.setStudentId(studentId);
        c.setWeeklyHours(hours);
        c.setStatus(status);
        return c;
    }

    private static Timesheet ts(String studentId, int week, String hours, String status) {
        Timesheet t = new Timesheet();
        t.setStudentId(studentId);
        t.setWeekNo(week);
        t.setHours(new BigDecimal(hours));
        t.setStatus(status);
        return t;
    }

    private static Budget budget(String total, String used, String frozen) {
        Budget b = new Budget();
        b.setId("B1");
        b.setName("测试经费");
        b.setTotalAmount(new BigDecimal(total));
        b.setUsedAmount(new BigDecimal(used));
        b.setFrozenAmount(new BigDecimal(frozen));
        return b;
    }

    private static Department dept(int limit) {
        Department d = new Department();
        d.setId("DEPT_CS");
        d.setName("计算机科学与技术学院");
        d.setQuotaLimit(limit);
        return d;
    }

    private static Job deptJob(int quota, String status) {
        Job j = job("J_" + quota + status, 8);
        j.setQuota(quota);
        j.setStatus(status);
        return j;
    }

    private static JobApplication app(String status) {
        JobApplication a = new JobApplication();
        a.setId("APP_X");
        a.setStudentId("S1");
        a.setJobId("J1");
        a.setStatus(status);
        return a;
    }

    // ---------------------------------------------------------------- 预算与名额

    @Nested
    class BudgetAndQuota {

        @Test
        void calcJobBudgetCostMultipliesAndNeverNegative() {
            assertThat(RuleEngine.calcJobBudgetCost(3, new BigDecimal("45"), 8, 16)).isEqualByComparingTo("17280");
            assertThat(RuleEngine.calcJobBudgetCost(-1, new BigDecimal("45"), 8, 16)).isEqualByComparingTo("0");
            assertThat(RuleEngine.calcJobBudgetCost(job("J", 8))).isEqualByComparingTo("11520");
        }

        @Test
        void budgetAvailableIsTotalMinusUsedMinusFrozen() {
            assertThat(RuleEngine.getBudgetAvailable(budget("120000", "58200", "17280"))).isEqualByComparingTo("44520");
            assertThat(RuleEngine.getBudgetAvailable(null)).isEqualByComparingTo("0");
        }

        @Test
        void deptQuotaIgnoresRejectedJobsAndOtherDepartments() {
            Job other = deptJob(5, "PUBLISHED");
            other.setDeptId("DEPT_MATH");
            var info = RuleEngine.getDeptQuotaUsage(dept(10),
                    "DEPT_CS", List.of(deptJob(3, "PUBLISHED"), deptJob(4, "REJECTED"), deptJob(2, "PENDING_DEPT_REVIEW"), other));
            assertThat(info.used()).isEqualTo(5);
            assertThat(info.limit()).isEqualTo(10);
            assertThat(info.remaining()).isEqualTo(5);
            assertThat(info.deptName()).isEqualTo("计算机科学与技术学院");

            var missing = RuleEngine.getDeptQuotaUsage(null, "DEPT_X", List.of());
            assertThat(missing.limit()).isEqualTo(20);
            assertThat(missing.deptName()).isEqualTo("DEPT_X");
        }

        @Test
        void validateJobPublishPasses() {
            var form = new RuleEngine.JobForm("DEPT_CS", "B1", 2, new BigDecimal("45"), 8, 16);
            var check = RuleEngine.validateJobPublish(form, budget("100000", "0", "0"), dept(10), List.of());
            assertThat(check.ok()).isTrue();
            assertThat(check.errors()).isEmpty();
            assertThat(check.estimatedCost()).isEqualByComparingTo("11520");
            assertThat(check.remainingAfter()).isEqualByComparingTo("88480");
        }

        @Test
        void validateJobPublishBlocksOverBudgetAndOverQuota() {
            var form = new RuleEngine.JobForm("DEPT_CS", "B1", 3, new BigDecimal("45"), 8, 16);
            var check = RuleEngine.validateJobPublish(form, budget("18000", "15800", "1000"), dept(4),
                    List.of(deptJob(2, "PUBLISHED")));
            assertThat(check.ok()).isFalse();
            assertThat(check.budgetExceeded()).isTrue();
            assertThat(check.deficit()).isEqualByComparingTo("16080");
            assertThat(check.quotaExceeded()).isTrue();
            assertThat(check.quotaDeficit()).isEqualTo(1);
            assertThat(check.errors()).containsExactly(
                    "预计总支出 ¥17,280 超出可用预算 ¥16,080，系统已拦截发布",
                    "招聘人数 3 人超出院系剩余名额 2 人，系统已拦截发布");
        }

        @Test
        void validateJobPublishWithoutBudget() {
            var form = new RuleEngine.JobForm("DEPT_CS", "B_NONE", 1, new BigDecimal("45"), 8, 16);
            var check = RuleEngine.validateJobPublish(form, null, dept(10), List.of());
            assertThat(check.ok()).isFalse();
            assertThat(check.errors()).containsExactly("请选择有效的经费来源账号");
        }
    }

    // ---------------------------------------------------------------- 审批流

    @Test
    void buildWorkflowStepsFiltersConditionalNodeAndAppendsPublished() {
        Map<String, Object> workflows = Map.of(
                "TA", List.of(
                        Map.of("key", "MENTOR_SUBMIT"),
                        Map.of("key", "DEPT_REVIEW"),
                        Map.of("key", "INTL_REVIEW", "conditional", "allowInternational"),
                        Map.of("key", "PUBLISHED")),
                "RA", List.of(Map.of("key", "MENTOR_SUBMIT"), Map.of("key", "DEPT_REVIEW")));
        assertThat(RuleEngine.buildWorkflowStepsForJob(workflows, "TA", true))
                .containsExactly("MENTOR_SUBMIT", "DEPT_REVIEW", "INTL_REVIEW", "PUBLISHED");
        assertThat(RuleEngine.buildWorkflowStepsForJob(workflows, "TA", false))
                .containsExactly("MENTOR_SUBMIT", "DEPT_REVIEW", "PUBLISHED");
        // 模板里没有 PUBLISHED 时自动补上；没有模板时只有 PUBLISHED
        assertThat(RuleEngine.buildWorkflowStepsForJob(workflows, "RA", false))
                .containsExactly("MENTOR_SUBMIT", "DEPT_REVIEW", "PUBLISHED");
        assertThat(RuleEngine.buildWorkflowStepsForJob(workflows, "GA", false)).containsExactly("PUBLISHED");
    }

    // ---------------------------------------------------------------- 工时

    @Nested
    class Hours {

        @Test
        void maxWeeklyHoursReadsRuleByNationalityAndPersonalCapOnlyTightens() {
            User intl = student("S1", true);
            User dom = student("S2", false);
            assertThat(RuleEngine.getMaxWeeklyHours(rules(20, 12), intl)).isEqualTo(12);
            assertThat(RuleEngine.getMaxWeeklyHours(rules(20, 12), dom)).isEqualTo(20);
            // 个人上限更严时取个人上限
            dom.setMaxWeeklyHours(10);
            assertThat(RuleEngine.getMaxWeeklyHours(rules(20, 12), dom)).isEqualTo(10);
            // 个人上限更宽松时不能放宽
            dom.setMaxWeeklyHours(30);
            assertThat(RuleEngine.getMaxWeeklyHours(rules(20, 12), dom)).isEqualTo(20);
            // 缺配置时默认 20，国际生项缺失时回落到默认项
            intl.setMaxWeeklyHours(null);
            assertThat(RuleEngine.getMaxWeeklyHours(Map.of(), intl)).isEqualTo(20);
            assertThat(RuleEngine.getMaxWeeklyHours(Map.of("maxWeeklyHoursDefault", 16), intl)).isEqualTo(16);
        }

        @Test
        void allocatedHoursCountsActiveAndPendingSignOnly() {
            List<Contract> cs = List.of(contract("S1", 8, "ACTIVE"), contract("S1", 4, "PENDING_SIGN"),
                    contract("S1", 10, "TERMINATED"), contract("S2", 6, "ACTIVE"));
            assertThat(RuleEngine.getStudentAllocatedWeeklyHours(cs, "S1")).isEqualTo(12);
            assertThat(RuleEngine.getStudentAllocatedWeeklyHours(cs, "S3")).isZero();
        }

        @Test
        void weekLoggedHoursExcludesRejectedAndOtherWeeks() {
            List<Timesheet> list = List.of(ts("S1", 12, "8", "APPROVED"), ts("S1", 12, "4.5", "PENDING"),
                    ts("S1", 12, "6", "REJECTED"), ts("S1", 11, "9", "APPROVED"));
            assertThat(RuleEngine.getStudentWeekLoggedHours(list, "S1", 12)).isEqualByComparingTo("12.5");
        }

        @Test
        void timesheetSubmissionPassesUnderCap() {
            var check = RuleEngine.validateTimesheetSubmission(student("S1", false), 12, new BigDecimal("8"),
                    List.of(ts("S1", 12, "8", "APPROVED")), rules(20, 20));
            assertThat(check.ok()).isTrue();
            assertThat(check.message()).isEqualTo("校验通过：第 12 周跨岗累计 16h / 20h");
        }

        @Test
        void timesheetSubmissionBlocksOverCapAndNonPositive() {
            var over = RuleEngine.validateTimesheetSubmission(student("S1", true), 12, new BigDecimal("13"),
                    List.of(), rules(20, 12));
            assertThat(over.ok()).isFalse();
            assertThat(over.message())
                    .isEqualTo("第 12 周跨岗累计工时将达 13h（已报 0h + 本次 13h），超出学校红线 12h/周，系统已拦截提交！");
            var zero = RuleEngine.validateTimesheetSubmission(student("S1", false), 12, BigDecimal.ZERO, List.of(),
                    rules(20, 20));
            assertThat(zero.ok()).isFalse();
            assertThat(zero.message()).isEqualTo("填报工时必须大于 0 小时");
        }
    }

    // ---------------------------------------------------------------- 申请

    @Nested
    class Application {

        @Test
        void domesticStudentPasses() {
            var check = RuleEngine.validateStudentApplication(student("S1", false), job("J1", 8), List.of(),
                    List.of(contract("S1", 8, "ACTIVE")), rules(20, 20));
            assertThat(check.passed()).isTrue();
            assertThat(check.checks()).extracting(RuleEngine.Check::key).containsExactly("hours");
            assertThat(check.checks().get(0).detail()).isEqualTo("当前已占 8h + 本岗 8h = 16h ≤ 上限 20h/周");
            assertThat(check.canSpecialApprove()).isFalse();
            assertThat(check.specialBlockedReason()).isEqualTo("合规校验已通过，无需特批");
            assertThat(check.currentHours()).isEqualTo(8);
            assertThat(check.projectedHours()).isEqualTo(16);
            assertThat(check.maxHours()).isEqualTo(20);
        }

        @Test
        void internationalStudentChecksVisaAllowIntlLanguageAndHours() {
            Job j = job("J1", 8);
            j.getExtraFields().put("minLangScore", 6.5);
            j.getExtraFields().put("languageRequirement", "IELTS 6.5 或同等水平");
            var check = RuleEngine.validateStudentApplication(student("S1", true), j, List.of(), List.of(),
                    rules(20, 20));
            assertThat(check.passed()).isTrue();
            assertThat(check.checks()).extracting(RuleEngine.Check::key).containsExactly("visa", "allowIntl", "lang", "hours");
            assertThat(check.checks().get(0).detail()).isEqualTo("X1学习居留许可有效（有效期至 2027-07-31）");
            assertThat(check.checks().get(0).overridable()).isFalse();
            assertThat(check.checks().get(2).detail()).isEqualTo("已认证 IELTS 7.0，满足岗位要求（IELTS 6.5 或同等水平）");
            assertThat(check.checks().get(2).overridable()).isTrue();
        }

        @Test
        void hoursOverCapCanBeSpeciallyApproved() {
            var check = RuleEngine.validateStudentApplication(student("S1", true), job("J1", 14), List.of(),
                    List.of(), rules(20, 12));
            assertThat(check.passed()).isFalse();
            RuleEngine.Check hours = check.checks().get(check.checks().size() - 1);
            assertThat(hours.passed()).isFalse();
            assertThat(hours.detail()).isEqualTo("当前已占 0h + 本岗 14h = 14h > 上限 12h/周（超出 2h）");
            assertThat(check.canSpecialApprove()).isTrue();
            assertThat(check.specialBlockedReason()).isEmpty();
        }

        @Test
        void invalidVisaIsHardFailureAndCannotBeSpeciallyApproved() {
            User s = student("S1", true);
            s.setVisaValid(false);
            Job j = job("J1", 8);
            j.setAllowInternational(false);
            var check = RuleEngine.validateStudentApplication(s, j, List.of(), List.of(), rules(20, 20));
            assertThat(check.passed()).isFalse();
            assertThat(check.canSpecialApprove()).isFalse();
            assertThat(check.specialBlockedReason())
                    .isEqualTo("签证类型校验（有效学习居留许可）、岗位国际生开放资格校验未通过，属于硬性条件，不可特批");
            assertThat(check.checks().get(0).detail()).isEqualTo("未检测到有效学习类居留许可，禁止从事校内助教/助研活动");
            assertThat(check.checks().get(1).detail()).isEqualTo("该岗位未面向国际学生开放（仅限中国籍学生申请）");
        }

        @Test
        void languageBelowRequirementIsOverridable() {
            User s = student("S1", false);
            s.setLanguageCert("IELTS 6.0");
            s.setLanguageLevel(new BigDecimal("6.0"));
            Job j = job("J1", 8);
            j.getExtraFields().put("minLangScore", 6.5);
            j.getExtraFields().put("languageRequirement", "IELTS 6.5 及以上");
            var check = RuleEngine.validateStudentApplication(s, j, List.of(), List.of(), rules(20, 20));
            assertThat(check.passed()).isFalse();
            assertThat(check.checks().get(0).detail()).isEqualTo("当前认证 IELTS 6.0（等效 6），低于岗位最低要求 IELTS 6.5 及以上");
            assertThat(check.canSpecialApprove()).isTrue();
        }

        @Test
        void alreadyAppliedAndQuotaFullBlock() {
            JobApplication existing = app("SUBMITTED");
            var dup = RuleEngine.validateStudentApplication(student("S1", false), job("J1", 8), List.of(existing),
                    List.of(), rules(20, 20));
            assertThat(dup.alreadyApplied()).isTrue();
            assertThat(dup.passed()).isFalse();
            assertThat(dup.specialBlockedReason()).isEqualTo("已申请过该岗位");

            // 之前被拒的申请不算“已申请”
            var again = RuleEngine.validateStudentApplication(student("S1", false), job("J1", 8),
                    List.of(app("REJECTED")), List.of(), rules(20, 20));
            assertThat(again.alreadyApplied()).isFalse();

            Job full = job("J1", 8);
            full.setHiredCount(2);
            var quota = RuleEngine.validateStudentApplication(student("S1", false), full, List.of(), List.of(),
                    rules(20, 20));
            assertThat(quota.quotaFull()).isTrue();
            assertThat(quota.specialBlockedReason()).isEqualTo("该岗位名额已招满");
        }
    }

    // ---------------------------------------------------------------- 录用

    @Nested
    class Hire {

        @Test
        void hirePassesWithinCap() {
            var check = RuleEngine.validateHire(app("INTERVIEW_SCHEDULED"), job("J1", 8), student("S1", false),
                    List.of(contract("S1", 8, "ACTIVE")), rules(20, 20));
            assertThat(check.ok()).isTrue();
            assertThat(check.projectedHours()).isEqualTo(16);
            assertThat(check.hoursOverridden()).isFalse();
        }

        @Test
        void hireBlockedWhenCumulativeHoursExceedCap() {
            var check = RuleEngine.validateHire(app("SUBMITTED"), job("J3", 8), student("S1", false),
                    List.of(contract("S1", 8, "ACTIVE"), contract("S1", 8, "PENDING_SIGN")), rules(20, 20));
            assertThat(check.ok()).isFalse();
            assertThat(check.errors()).containsExactly(
                    "录用后跨岗累计周工时将达 24h（已占 16h + 本岗 8h），超出上限 20h/周，系统已拦截录用");
        }

        @Test
        void specialApprovalCoversOnlyTheApprovedCeiling() {
            JobApplication a = app("SPECIAL_APPROVED");
            a.setSpecialStatus("APPROVED");
            Map<String, Object> ccr = new HashMap<>();
            ccr.put("projectedHours", 14);
            a.setComplianceCheckResult(ccr);
            // 特批时 14h，录用时仍是 14h：放行
            var ok = RuleEngine.validateHire(a, job("J1", 14), student("S1", true), List.of(), rules(20, 12));
            assertThat(ok.ok()).isTrue();
            assertThat(ok.hoursOverridden()).isTrue();
            // 特批后又多了 4h 合同：超出特批幅度，拦截
            var blocked = RuleEngine.validateHire(a, job("J1", 14), student("S1", true),
                    List.of(contract("S1", 4, "ACTIVE")), rules(20, 12));
            assertThat(blocked.ok()).isFalse();
        }

        @Test
        void hireBlockedByStatusQuotaVisaAndAllowIntl() {
            User s = student("S1", true);
            s.setVisaValid(false);
            Job j = job("J1", 2);
            j.setHiredCount(2);
            j.setAllowInternational(false);
            var check = RuleEngine.validateHire(app("HIRED"), j, s, List.of(), rules(20, 20));
            assertThat(check.errors()).containsExactly(
                    "申请当前状态为「已录用」，不能录用",
                    "该岗位招聘名额已满，无法继续录用",
                    "该国际生无有效学习类居留许可，不能录用",
                    "该岗位未面向国际学生开放，不能录用国际生");
        }
    }

    // ---------------------------------------------------------------- 考核

    @Test
    void gradeFromScoreBands() {
        assertThat(RuleEngine.gradeFromScore(new BigDecimal("93.5")))
                .isEqualTo(new RuleEngine.GradeInfo("优秀", new BigDecimal("1.1"), true, "success"));
        assertThat(RuleEngine.gradeFromScore(new BigDecimal("90")).grade()).isEqualTo("优秀");
        assertThat(RuleEngine.gradeFromScore(new BigDecimal("60")).grade()).isEqualTo("合格");
        assertThat(RuleEngine.gradeFromScore(new BigDecimal("59.9")))
                .isEqualTo(new RuleEngine.GradeInfo("不合格", new BigDecimal("0.85"), false, "error"));
        assertThat(RuleEngine.gradeFromScore(null).grade()).isEqualTo("不合格");
    }

    // ---------------------------------------------------------------- 薪酬与预算联动

    @Nested
    class PayrollBudget {

        @Test
        void amountRoundsToCents() {
            assertThat(RuleEngine.calcPayrollAmount(new BigDecimal("24"), new BigDecimal("48"), new BigDecimal("1.1")))
                    .isEqualByComparingTo("1267.20");
            assertThat(RuleEngine.calcPayrollAmount(new BigDecimal("7.5"), new BigDecimal("33.33"), new BigDecimal("0.85")))
                    .isEqualByComparingTo("212.48");
        }

        @Test
        void passesWhenCoveredByReserveAndAvailable() {
            // 剩余预留 1000 − 400 = 600，可用额 1000 − 0 − 600 = 400，上限 1000
            var check = RuleEngine.checkPayrollBudget(new BigDecimal("1000"), new BigDecimal("1000"),
                    new BigDecimal("400"), budget("1000", "0", "600"));
            assertThat(check.ok()).isTrue();
            assertThat(check.reserveRemaining()).isEqualByComparingTo("600");
            assertThat(check.budgetAvailable()).isEqualByComparingTo("400");
        }

        @Test
        void blockedWhenExceedingReservePlusAvailable() {
            var check = RuleEngine.checkPayrollBudget(new BigDecimal("1000.01"), new BigDecimal("1000"),
                    new BigDecimal("400"), budget("1000", "0", "600"));
            assertThat(check.ok()).isFalse();
            assertThat(check.reason()).isEqualTo("核算金额 ¥1,000.01 超出岗位剩余预留 ¥600 与经费可用额 ¥400 之和");
            // 已计入薪酬超过预留时，剩余预留不低于 0
            assertThat(RuleEngine.reserveRemaining(new BigDecimal("100"), new BigDecimal("300"))).isEqualByComparingTo("0");
        }

        @Test
        void payoutReleasesAtMostTheJobsRemainingReserve() {
            assertThat(RuleEngine.payoutFrozenRelease(new BigDecimal("600"), new BigDecimal("1000"), new BigDecimal("0")))
                    .isEqualByComparingTo("600");
            assertThat(RuleEngine.payoutFrozenRelease(new BigDecimal("600"), new BigDecimal("1000"), new BigDecimal("600")))
                    .isEqualByComparingTo("400");
            assertThat(RuleEngine.payoutFrozenRelease(new BigDecimal("600"), new BigDecimal("1000"), new BigDecimal("1200")))
                    .isEqualByComparingTo("0");
        }
    }
}
