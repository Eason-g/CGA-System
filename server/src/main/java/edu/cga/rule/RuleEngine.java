package edu.cga.rule;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import edu.cga.common.Num;
import edu.cga.domain.entity.Budget;
import edu.cga.domain.entity.Contract;
import edu.cga.domain.entity.Department;
import edu.cga.domain.entity.Job;
import edu.cga.domain.entity.JobApplication;
import edu.cga.domain.entity.Timesheet;
import edu.cga.domain.entity.User;

/**
 * 规则引擎：src/services/ruleEngine.js 的 Java 版（设计文档第 7 节）。
 * <p>
 * 全部是纯函数：入参是 Service 已经从数据库取出（并按需加锁）的数据，这里不访问数据库。
 * 返回对象的字段名与中文文案与 JS 版逐字一致，前端仍用 JS 版做提前提示，两边口径相同。
 * 文件末尾的“薪酬与预算联动”是 P0 新增规则，mock 里没有对应函数。
 */
public final class RuleEngine {

    private RuleEngine() {
    }

    // ================================================================== 常量

    /** 流程节点 → 岗位状态（同 JS STEP_TO_JOB_STATUS） */
    public static final Map<String, String> STEP_TO_JOB_STATUS = Map.of(
            "DEPT_REVIEW", "PENDING_DEPT_REVIEW",
            "INTL_REVIEW", "PENDING_INTL_REVIEW",
            "SCHOOL_REVIEW", "PENDING_SCHOOL_REVIEW",
            "PUBLISHED", "PUBLISHED");

    /** 岗位状态文案（同 JS JOB_STATUS_MAP 的 label） */
    public static final Map<String, String> JOB_STATUS_LABELS = Map.of(
            "DRAFT", "草稿",
            "PENDING_DEPT_REVIEW", "待院系初审",
            "PENDING_INTL_REVIEW", "待国际生办会签",
            "PENDING_SCHOOL_REVIEW", "待校级终审",
            "PUBLISHED", "已正式发布",
            "REJECTED", "已驳回",
            "CLOSED", "已招满关闭");

    /** 申请状态文案（同 JS APP_STATUS_MAP 的 label） */
    public static final Map<String, String> APP_STATUS_LABELS = Map.of(
            "SUBMITTED", "待导师筛选",
            "PENDING_SPECIAL_APPROVAL", "待国际生办特批",
            "SPECIAL_APPROVED", "特批通过·待筛选",
            "SPECIAL_REJECTED", "特批驳回",
            "INTERVIEW_SCHEDULED", "已安排面试",
            "HIRED", "已录用",
            "REJECTED", "未录用");

    /** 可以录用的申请状态（同 JS HIREABLE_APP_STATUSES） */
    public static final List<String> HIREABLE_APP_STATUSES = List.of("SUBMITTED", "SPECIAL_APPROVED", "INTERVIEW_SCHEDULED");

    /** 计入学生“已占周工时”的合同状态 */
    public static final List<String> ALLOCATING_CONTRACT_STATUSES = List.of("ACTIVE", "PENDING_SIGN");

    /** 未在配置中心设置时的默认周工时上限 */
    public static final int DEFAULT_MAX_WEEKLY_HOURS = 20;

    /** 院系不存在时的默认名额上限（同 JS） */
    public static final int DEFAULT_DEPT_QUOTA = 20;

    // ================================================================== 结果对象（字段名同 JS 返回值）

    /** 院系名额占用 */
    public record QuotaInfo(String deptName, int used, int limit, int remaining) {
    }

    /** 建岗表单中参与校验的字段 */
    public record JobForm(String deptId, String budgetId, int quota, BigDecimal hourlyRate, int weeklyHours, int weeks) {
    }

    /** validateJobPublish 的结果 */
    public record JobPublishCheck(boolean ok, BigDecimal estimatedCost, BigDecimal availableBudget,
                                  BigDecimal remainingAfter, BigDecimal deficit, boolean budgetExceeded,
                                  Budget budget, QuotaInfo quotaInfo, boolean quotaExceeded, int quotaDeficit,
                                  List<String> errors) {
    }

    /** 单项合规校验；overridable=false 表示硬性条件，不允许通过特批绕过 */
    public record Check(String key, String label, boolean passed, boolean overridable, String detail) {
    }

    /**
     * validateStudentApplication 的结果。
     * hints 为提示性检查项（P1 第三批：签证有效期是否覆盖预计聘期），不影响 passed，也不参与特批判断。
     */
    public record ApplicationCheck(boolean passed, boolean alreadyApplied, JobApplication existingApp,
                                   boolean quotaFull, List<Check> checks, int currentHours, int jobHours,
                                   int projectedHours, int maxHours, boolean canSpecialApprove,
                                   String specialBlockedReason, List<Check> hints) {
    }

    /** validateHire 的结果 */
    public record HireCheck(boolean ok, List<String> errors, int currentHours, int jobHours, int projectedHours,
                            int maxHours, boolean hoursOverridden) {
    }

    /** validateTimesheetSubmission 的结果 */
    public record TimesheetCheck(boolean ok, BigDecimal existingHours, BigDecimal projectedHours, int maxHours,
                                 String message) {
    }

    /** gradeFromScore 的结果 */
    public record GradeInfo(String grade, BigDecimal coefficient, boolean renewalEligible, String tone) {
    }

    /** 薪酬预算校验结果（P0 新增） */
    public record PayrollBudgetCheck(boolean ok, BigDecimal amount, BigDecimal reserveRemaining,
                                     BigDecimal budgetAvailable, String reason) {
    }

    // ================================================================== 预算与名额

    /** 岗位预计总成本 = 名额 × 时薪 × 周工时 × 周数（不低于 0） */
    public static BigDecimal calcJobBudgetCost(int quota, BigDecimal hourlyRate, int weeklyHours, int weeks) {
        BigDecimal cost = BigDecimal.valueOf(quota)
                .multiply(Num.orZero(hourlyRate))
                .multiply(BigDecimal.valueOf(weeklyHours))
                .multiply(BigDecimal.valueOf(weeks));
        return cost.max(BigDecimal.ZERO);
    }

    public static BigDecimal calcJobBudgetCost(Job job) {
        return calcJobBudgetCost(Num.orZero(job.getQuota()), job.getHourlyRate(),
                Num.orZero(job.getWeeklyHours()), Num.orZero(job.getWeeks()));
    }

    /** 经费可用额 = 总额 − 已发放 − 冻结 */
    public static BigDecimal getBudgetAvailable(Budget budget) {
        if (budget == null) {
            return BigDecimal.ZERO;
        }
        return Num.orZero(budget.getTotalAmount())
                .subtract(Num.orZero(budget.getUsedAmount()))
                .subtract(Num.orZero(budget.getFrozenAmount()));
    }

    /**
     * 院系名额占用：jobs 中属于该院系且未驳回的岗位名额之和。
     *
     * @param dept 院系（可为 null，此时上限取默认 20）
     * @param jobs 岗位列表（函数内按 deptId 过滤，传本院系岗位或全部岗位均可）
     */
    public static QuotaInfo getDeptQuotaUsage(Department dept, String deptId, Collection<Job> jobs) {
        int used = jobs.stream()
                .filter(j -> Objects.equals(j.getDeptId(), deptId) && !"REJECTED".equals(j.getStatus()))
                .mapToInt(j -> Num.orZero(j.getQuota()))
                .sum();
        int limit = dept != null ? Num.orZero(dept.getQuotaLimit()) : DEFAULT_DEPT_QUOTA;
        String deptName = dept != null && dept.getName() != null ? dept.getName() : deptId;
        return new QuotaInfo(deptName, used, limit, Math.max(0, limit - used));
    }

    /** 岗位发布硬校验：预算不足、名额不足都拦截 */
    public static JobPublishCheck validateJobPublish(JobForm form, Budget budget, Department dept, Collection<Job> jobs) {
        return validateJobPublish(form, budget, dept, jobs, BigDecimal.ZERO);
    }

    public static JobPublishCheck validateJobPublish(JobForm form, Budget budget, Department dept,
                                                    Collection<Job> jobs, BigDecimal unpaidExcess) {
        BigDecimal estimatedCost = calcJobBudgetCost(form.quota(), form.hourlyRate(), form.weeklyHours(), form.weeks());
        BigDecimal availableBudget = getBudgetAvailable(budget).subtract(unpaidExcess);
        BigDecimal deficit = estimatedCost.subtract(availableBudget).max(BigDecimal.ZERO);
        boolean budgetExceeded = budget == null || estimatedCost.compareTo(availableBudget) > 0;

        QuotaInfo quotaInfo = getDeptQuotaUsage(dept, form.deptId(), jobs);
        int requestedQuota = form.quota();
        boolean quotaExceeded = requestedQuota > quotaInfo.remaining();
        int quotaDeficit = Math.max(0, requestedQuota - quotaInfo.remaining());

        List<String> errors = new ArrayList<>();
        if (budget == null) {
            errors.add("请选择有效的经费来源账号");
        }
        if (budget != null && budgetExceeded) {
            errors.add("预计总支出 ¥" + Num.locale(estimatedCost) + " 超出可用预算 ¥" + Num.locale(deficit)
                    + "，系统已拦截发布");
        }
        if (quotaExceeded) {
            errors.add("招聘人数 " + requestedQuota + " 人超出院系剩余名额 " + quotaInfo.remaining() + " 人，系统已拦截发布");
        }
        return new JobPublishCheck(errors.isEmpty(), estimatedCost, availableBudget,
                availableBudget.subtract(estimatedCost), deficit, budgetExceeded, budget, quotaInfo,
                quotaExceeded, quotaDeficit, errors);
    }

    /**
     * 按配置中心的审批流模板生成岗位的流程节点；带 conditional=allowInternational 的节点只在面向国际生时保留。
     *
     * @param workflows configs.workflows，形如 { TA: [{key, label, role, conditional?}], RA: [...], GA: [...] }
     */
    public static List<String> buildWorkflowStepsForJob(Map<String, Object> workflows, String jobType,
                                                        boolean allowInternational) {
        List<String> steps = new ArrayList<>();
        Object template = workflows == null ? null : workflows.get(jobType);
        if (template instanceof List<?> nodes) {
            for (Object n : nodes) {
                if (!(n instanceof Map<?, ?> node)) {
                    continue;
                }
                if ("allowInternational".equals(node.get("conditional")) && !allowInternational) {
                    continue;
                }
                Object key = node.get("key");
                if (key != null) {
                    steps.add(key.toString());
                }
            }
        }
        steps.removeIf("PUBLISHED"::equals);
        if (steps.isEmpty() || !"MENTOR_SUBMIT".equals(steps.get(0))) {
            steps.add(0, "MENTOR_SUBMIT");
        }
        if (allowInternational && !steps.contains("INTL_REVIEW")) {
            int schoolReview = steps.indexOf("SCHOOL_REVIEW");
            steps.add(schoolReview < 0 ? steps.size() : schoolReview, "INTL_REVIEW");
        }
        steps.add("PUBLISHED");
        return steps;
    }

    // ================================================================== 工时

    /**
     * 周工时上限：国际生读 globalRules.maxWeeklyHoursIntl，国内生读 maxWeeklyHoursDefault；
     * 学生个人上限（学籍/签证给出）只能更严，不能放宽。
     */
    public static int getMaxWeeklyHours(Map<String, Object> globalRules, User student) {
        Map<String, Object> rules = globalRules == null ? Map.of() : globalRules;
        int fallback = numberOr(rules.get("maxWeeklyHoursDefault"), DEFAULT_MAX_WEEKLY_HOURS);
        boolean intl = student != null && Boolean.TRUE.equals(student.getIsInternational());
        int ruleCap = intl ? numberOr(rules.get("maxWeeklyHoursIntl"), fallback) : fallback;
        Integer personalCap = student == null ? null : student.getMaxWeeklyHours();
        return personalCap != null && personalCap > 0 ? Math.min(personalCap, ruleCap) : ruleCap;
    }

    /**
     * 学生已占周工时：生效和待签合同在<b>同一时间</b>叠加的周工时峰值。
     * <p>
     * 不是简单求和：续聘生成的新合同从原合同结束次日开始，两者在时间上不重叠，学生每周实际只干一份。
     * 求和会把它算成两份，导致原合同到期前去应聘其他岗位被误拦。时间上重叠的合同照常相加。
     * 没有起止日期的合同视为一直有效（保守处理）。
     */
    public static int getStudentAllocatedWeeklyHours(Collection<Contract> contracts, String studentId) {
        return getStudentAllocatedWeeklyHours(contracts, studentId, null, null);
    }

    /** 在候选聘期内计算既有合同的同时占用峰值。边界按合同日期含当天。 */
    public static int getStudentAllocatedWeeklyHours(Collection<Contract> contracts, String studentId,
                                                      LocalDate start, LocalDate end) {
        List<Contract> mine = contracts.stream()
                .filter(c -> Objects.equals(c.getStudentId(), studentId)
                        && ALLOCATING_CONTRACT_STATUSES.contains(c.getStatus()))
                .toList();
        int peak = 0;
        // 叠加量只会在某份合同开始的那一天上升，所以只需检查每份合同的开始日
        for (Contract c : mine) {
            LocalDate day = c.getStartDate() == null ? LocalDate.MIN : c.getStartDate();
            if (start != null && day.isBefore(start)) {
                day = start;
            }
            if (end != null && day.isAfter(end)) {
                continue;
            }
            int load = 0;
            for (Contract d : mine) {
                boolean started = d.getStartDate() == null || !d.getStartDate().isAfter(day);
                boolean notEnded = d.getEndDate() == null || !d.getEndDate().isBefore(day);
                if (started && notEnded) {
                    load += Num.orZero(d.getWeeklyHours());
                }
            }
            peak = Math.max(peak, load);
        }
        return peak;
    }

    /** 学生某周已填报工时（跨岗累计，不含已驳回） */
    public static BigDecimal getStudentWeekLoggedHours(Collection<Timesheet> timesheets, String studentId, int weekNo) {
        return timesheets.stream()
                .filter(t -> Objects.equals(t.getStudentId(), studentId)
                        && t.getWeekNo() != null && t.getWeekNo() == weekNo
                        && !"REJECTED".equals(t.getStatus()))
                .map(t -> Num.orZero(t.getHours()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ================================================================== 申请

    /**
     * 学生申请前置合规校验。签证、学籍、截止日、岗位国际生开放资格为硬性条件；语言、跨岗工时可特批（需求原文：超限需特批留痕）。
     *
     * @param applications 该生的申请（函数内按学生和岗位过滤）
     * @param contracts    该生的合同（函数内按学生和状态过滤）
     */
    public static ApplicationCheck validateStudentApplication(User student, Job job,
                                                              Collection<JobApplication> applications,
                                                              Collection<Contract> contracts,
                                                              Map<String, Object> globalRules) {
        return validateStudentApplication(student, job, applications, contracts, globalRules, null);
    }

    /**
     * 同上；today 不为空时，国际生额外得到“签证有效期覆盖聘期”的提示项（只提示、不拦截，录用时才硬拦截）。
     */
    public static ApplicationCheck validateStudentApplication(User student, Job job,
                                                              Collection<JobApplication> applications,
                                                              Collection<Contract> contracts,
                                                              Map<String, Object> globalRules,
                                                              LocalDate today) {
        List<Check> checks = new ArrayList<>();
        List<Check> hints = new ArrayList<>();
        JobApplication existingApp = applications.stream()
                .filter(a -> Objects.equals(a.getStudentId(), student.getId())
                        && Objects.equals(a.getJobId(), job.getId())
                        && !"REJECTED".equals(a.getStatus())
                        && !"SPECIAL_REJECTED".equals(a.getStatus()))
                .findFirst().orElse(null);
        boolean quotaFull = Num.orZero(job.getHiredCount()) >= Num.orZero(job.getQuota());

        LocalDate start = today != null ? contractStartFrom(today) : null;
        LocalDate end = today != null ? predictContractEnd(today, Num.orZero(job.getWeeks())) : null;
        int currentHours = getStudentAllocatedWeeklyHours(contracts, student.getId(), start, end);
        int jobHours = Num.orZero(job.getWeeklyHours());
        int projectedHours = currentHours + jobHours;
        int maxHours = getMaxWeeklyHours(globalRules, student);

        if (today != null && job.getDeadline() != null && job.getDeadline().isBefore(today)) {
            checks.add(new Check("deadline", "申请截止日期", false, false,
                    "已过申请截止日期（" + job.getDeadline() + "）"));
        }

        // 0. 学籍状态（P2 B.2）：必须在读，硬性条件不可特批；在读时不额外列出，保持原有校验项顺序
        String enrollmentError = checkEnrollment(student);
        if (enrollmentError != null) {
            checks.add(new Check("enrollment", "学籍状态校验（在读）", false, false, enrollmentError));
        }

        if (Boolean.TRUE.equals(student.getIsInternational())) {
            // 1. 签证
            boolean visaOk = hasValidVisa(student, today);
            String expiry = student.getVisaExpiry() != null ? student.getVisaExpiry().toString() : "未登记";
            checks.add(new Check("visa", "签证类型校验（有效学习居留许可）", visaOk, false,
                    visaOk ? student.getVisaType() + "有效（有效期至 " + expiry + "）"
                            : "未检测到有效学习类居留许可，禁止从事校内助教/助研活动"));

            // 2. 岗位是否面向国际生
            boolean allowIntlOk = allowsInternationalEmployment(job);
            checks.add(new Check("allowIntl", "岗位国际生开放资格校验", allowIntlOk, false,
                    allowIntlOk ? "该岗位已通过国际生办会签，面向国际学生开放"
                            : "该岗位未面向国际学生开放（仅限中国籍学生申请）"));

            // 3. 签证有效期覆盖预计聘期（提示性，不拦截；录用时由 validateHire 硬拦截）
            if (today != null && visaOk) {
                boolean covered = checkVisaCoverage(student, end) == null;
                hints.add(new Check("visaPeriod", "签证有效期覆盖聘期（提示）", covered, false,
                        covered ? "签证有效期至 " + expiry + "，覆盖预计聘期（至 " + end + "）"
                                : "签证有效期至 " + expiry + "，早于预计聘期结束日 " + end
                                + "，录用时将被系统拦截，请尽快办理续签并上传新的签证材料"));
            }
        }

        // 语言能力（岗位设置了 minLangScore 才校验）
        Map<String, Object> extra = job.getExtraFields();
        BigDecimal reqScore = extra == null ? null : Num.toDecimal(extra.get("minLangScore"));
        if (reqScore != null && reqScore.signum() != 0) {
            BigDecimal stuScore = Num.orZero(student.getLanguageLevel());
            boolean langOk = Boolean.TRUE.equals(student.getLanguageValid()) && stuScore.compareTo(reqScore) >= 0;
            String requirement = Objects.toString(extra.get("languageRequirement"), "");
            String cert = notBlank(student.getLanguageCert()) ? student.getLanguageCert() : "未提交";
            // 新上传的语言证明在国际生办核验前视为未通过（可特批）
            boolean pending = LANG_CERT_PENDING.equals(student.getLanguageCertStatus());
            checks.add(new Check("lang", "语言能力证明校验", langOk, true,
                    langOk ? "已认证 " + student.getLanguageCert() + "，满足岗位要求（" + requirement + "）"
                            : pending ? "语言证明已提交，待国际生办核验（核验前视为未通过），岗位要求 " + requirement
                            : "当前认证 " + cert + "（等效 " + Num.plain(stuScore) + "），低于岗位最低要求 " + requirement));
        }

        // 跨岗累计周工时（国际生、国内生都校验）
        boolean hoursOk = projectedHours <= maxHours;
        checks.add(new Check("hours", "跨岗累计周工时校验", hoursOk, true,
                hoursOk ? "当前已占 " + currentHours + "h + 本岗 " + jobHours + "h = " + projectedHours + "h ≤ 上限 "
                        + maxHours + "h/周"
                        : "当前已占 " + currentHours + "h + 本岗 " + jobHours + "h = " + projectedHours + "h > 上限 "
                        + maxHours + "h/周（超出 " + (projectedHours - maxHours) + "h）"));

        boolean allPassed = checks.stream().allMatch(Check::passed) && existingApp == null && !quotaFull;
        List<Check> failed = checks.stream().filter(c -> !c.passed()).toList();
        List<Check> hardFailures = failed.stream().filter(c -> !c.overridable()).toList();
        // 只有可特批项（工时、语言）未通过时才能发起特批；签证、岗位开放资格属于硬性条件
        boolean canSpecialApprove = existingApp == null && !quotaFull && !failed.isEmpty() && hardFailures.isEmpty();
        String specialBlockedReason = "";
        if (!canSpecialApprove) {
            if (existingApp != null) {
                specialBlockedReason = "已申请过该岗位";
            } else if (quotaFull) {
                specialBlockedReason = "该岗位名额已招满";
            } else if (failed.isEmpty()) {
                specialBlockedReason = "合规校验已通过，无需特批";
            } else {
                specialBlockedReason = String.join("、", hardFailures.stream().map(Check::label).toList())
                        + "未通过，属于硬性条件，不可特批";
            }
        }
        return new ApplicationCheck(allPassed, existingApp != null, existingApp, quotaFull, checks, currentHours,
                jobHours, projectedHours, maxHours, canSpecialApprove, specialBlockedReason, hints);
    }

    // ================================================================== 录用

    /**
     * 录用时复核：申请后学生可能又被其他岗位录用，必须按录用时刻的累计工时重新判断。
     * 超限一律拦截，除非申请已获特批且录用后工时不超过特批时记录的 projectedHours（国际生、国内生相同）。
     *
     * @param contracts 该生当前的合同（Service 已锁住学生行后读取）
     */
    public static HireCheck validateHire(JobApplication app, Job job, User student, Collection<Contract> contracts,
                                         Map<String, Object> globalRules) {
        return validateHire(app, job, student, contracts, globalRules, null);
    }

    /**
     * 同上；contractEndDate 不为空时，国际生的签证有效期必须覆盖到该日期，否则拦截且不可特批。
     */
    public static HireCheck validateHire(JobApplication app, Job job, User student, Collection<Contract> contracts,
                                         Map<String, Object> globalRules, LocalDate contractEndDate) {
        LocalDate start = contractEndDate != null
                ? contractEndDate.minusWeeks(Math.max(1, Num.orZero(job.getWeeks()))) : null;
        List<String> errors = new ArrayList<>();
        if (!HIREABLE_APP_STATUSES.contains(app.getStatus())) {
            String label = APP_STATUS_LABELS.getOrDefault(app.getStatus(), app.getStatus());
            errors.add("申请当前状态为「" + label + "」，不能录用");
        }
        if (Num.orZero(job.getHiredCount()) >= Num.orZero(job.getQuota())) {
            errors.add("该岗位招聘名额已满，无法继续录用");
        }
        // 学籍状态必须为在读（P2 B.2，硬性条件，特批也不能放行）
        if (checkEnrollment(student) != null) {
            errors.add("该学生当前学籍状态为「" + enrollmentStatusOf(student) + "」，仅在读学生可录用（不可特批）");
        }
        if (Boolean.TRUE.equals(student.getIsInternational())) {
            if (!hasValidVisa(student, null)) {
                errors.add("该国际生无有效学习类居留许可，不能录用");
            }
            if (!allowsInternationalEmployment(job)) {
                errors.add("该岗位未面向国际学生开放，不能录用国际生");
            }
            // 签证有效期必须覆盖整个聘期（硬性条件，特批也不能放行）
            if (contractEndDate != null && Boolean.TRUE.equals(student.getVisaValid())) {
                String coverageError = checkVisaCoverage(student, contractEndDate);
                if (coverageError != null) {
                    errors.add(coverageError);
                }
            }
        }

        int currentHours = getStudentAllocatedWeeklyHours(contracts, student.getId(), start, contractEndDate);
        int jobHours = Num.orZero(job.getWeeklyHours());
        int projectedHours = currentHours + jobHours;
        int maxHours = getMaxWeeklyHours(globalRules, student);
        boolean hoursOverridden = false;
        if (projectedHours > maxHours) {
            // 特批只覆盖审批时看到的超限幅度；之后又多出来的工时需要重新审批
            Map<String, Object> ccr = app.getComplianceCheckResult();
            BigDecimal approvedCeiling = ccr == null ? null : Num.toDecimal(ccr.get("projectedHours"));
            if ("APPROVED".equals(app.getSpecialStatus()) && approvedCeiling != null
                    && BigDecimal.valueOf(projectedHours).compareTo(approvedCeiling) <= 0) {
                hoursOverridden = true;
            } else {
                errors.add("录用后跨岗累计周工时将达 " + projectedHours + "h（已占 " + currentHours + "h + 本岗 " + jobHours
                        + "h），超出上限 " + maxHours + "h/周，系统已拦截录用");
            }
        }
        return new HireCheck(errors.isEmpty(), errors, currentHours, jobHours, projectedHours, maxHours, hoursOverridden);
    }

    // ================================================================== 学籍状态（P2 B.2）

    /** 学籍状态：在读（唯一允许申请、录用的状态） */
    public static final String ENROLLMENT_ACTIVE = "在读";
    /** 学籍系统可能返回的全部状态 */
    public static final List<String> ENROLLMENT_STATUSES = List.of("在读", "休学", "毕业");

    public static boolean allowsInternationalEmployment(Job job) {
        if (!Boolean.TRUE.equals(job.getAllowInternational()) || job.getWorkflowSteps() == null
                || job.getCurrentStepIndex() == null) {
            return false;
        }
        int review = job.getWorkflowSteps().indexOf("INTL_REVIEW");
        return review >= 0 && review < job.getCurrentStepIndex();
    }

    public static boolean hasValidVisa(User student, LocalDate onDate) {
        return Boolean.TRUE.equals(student.getVisaValid()) && notBlank(student.getVisaType())
                && (onDate == null || student.getVisaExpiry() == null || !student.getVisaExpiry().isBefore(onDate));
    }

    /** 合规复核用于合同生效及履约，使用当前学籍与指定期间的签证覆盖。 */
    public static String checkWorkingEligibility(User student, LocalDate through) {
        if (checkEnrollment(student) != null) {
            return "当前学籍状态为「" + enrollmentStatusOf(student) + "」，仅在读学生可办理聘用或工时";
        }
        if (Boolean.TRUE.equals(student.getIsInternational())) {
            if (!hasValidVisa(student, null)) {
                return "该国际生无有效学习类居留许可";
            }
            if (through == null || student.getVisaExpiry() == null || student.getVisaExpiry().isBefore(through)) {
                return "签证（居留许可）未覆盖办理日期或聘期";
            }
        }
        return null;
    }

    /** 学生的学籍状态；未登记（如历史数据）按在读处理（同 JS enrollmentStatusOf） */
    public static String enrollmentStatusOf(User student) {
        String s = student.getEnrollmentStatus();
        return s == null || s.isBlank() ? ENROLLMENT_ACTIVE : s.trim();
    }

    /** 学籍状态为在读返回 null，否则返回拦截文案（同 JS checkEnrollment） */
    public static String checkEnrollment(User student) {
        String s = enrollmentStatusOf(student);
        if (ENROLLMENT_ACTIVE.equals(s)) {
            return null;
        }
        return "当前学籍状态为「" + s + "」，仅在读学生可申请三助岗位（硬性条件，不可特批）";
    }

    // ================================================================== 合规强化（P1 第三批）

    /** 语言证明状态：学生新上传、待国际生办核验（核验前语言项视为未通过） */
    public static final String LANG_CERT_PENDING = "PENDING";
    /** 语言证明状态：国际生办核验通过 */
    public static final String LANG_CERT_VERIFIED = "VERIFIED";
    /** 语言证明状态：国际生办核验不通过 */
    public static final String LANG_CERT_REJECTED = "REJECTED";

    /** 签证到期预警天数（前端 scanAnomalies 的“签证 30 天内到期”与此一致） */
    public static final int VISA_EXPIRY_WARNING_DAYS = 30;

    /** 聘期开始日：今天之后的下一个周一（同 JS contractStartFrom） */
    public static LocalDate contractStartFrom(LocalDate today) {
        return today.with(TemporalAdjusters.next(DayOfWeek.MONDAY));
    }

    /** 预计聘期结束日：开始日 + 岗位约定周数（至少 1 周）（同 JS predictContractEnd） */
    public static LocalDate predictContractEnd(LocalDate today, int weeks) {
        return contractStartFrom(today).plusWeeks(Math.max(1, weeks));
    }

    /**
     * 签证有效期是否覆盖到 endDate（录用、续聘共用）。覆盖返回 null，否则返回拦截文案（不可特批）。
     * 未登记有效期同样视为不覆盖。
     */
    public static String checkVisaCoverage(User student, LocalDate endDate) {
        LocalDate expiry = student.getVisaExpiry();
        if (expiry == null) {
            return "该国际生未登记签证有效期，无法确认覆盖聘期结束日 " + endDate + "，不能录用（不可特批）";
        }
        if (expiry.isBefore(endDate)) {
            return "该国际生签证有效期至 " + expiry + "，早于聘期结束日 " + endDate + "，签证须覆盖整个聘期，不能录用（不可特批）";
        }
        return null;
    }

    /** 国际生签证是否将在 VISA_EXPIRY_WARNING_DAYS 天内到期（含已过期） */
    public static boolean isVisaExpiringSoon(User student, LocalDate today) {
        LocalDate expiry = student.getVisaExpiry();
        return Boolean.TRUE.equals(student.getIsInternational()) && expiry != null
                && !expiry.isAfter(today.plusDays(VISA_EXPIRY_WARNING_DAYS));
    }

    // ================================================================== 工时填报

    /**
     * 周工时填报：按周跨岗累计，超上限拦截。
     *
     * @param timesheets 该生的工时单（函数内按学生和周次过滤）
     */
    public static TimesheetCheck validateTimesheetSubmission(User student, int weekNo, BigDecimal newHours,
                                                             Collection<Timesheet> timesheets,
                                                             Map<String, Object> globalRules) {
        int maxHours = getMaxWeeklyHours(globalRules, student);
        BigDecimal existingHours = getStudentWeekLoggedHours(timesheets, student.getId(), weekNo);
        BigDecimal hoursNum = Num.orZero(newHours);
        BigDecimal projectedHours = existingHours.add(hoursNum);

        if (hoursNum.signum() <= 0) {
            return new TimesheetCheck(false, existingHours, projectedHours, maxHours, "填报工时必须大于 0 小时");
        }
        if (projectedHours.compareTo(BigDecimal.valueOf(maxHours)) > 0) {
            return new TimesheetCheck(false, existingHours, projectedHours, maxHours,
                    "第 " + weekNo + " 周跨岗累计工时将达 " + Num.plain(projectedHours) + "h（已报 " + Num.plain(existingHours)
                            + "h + 本次 " + Num.plain(hoursNum) + "h），超出学校红线 " + maxHours + "h/周，系统已拦截提交！");
        }
        return new TimesheetCheck(true, existingHours, projectedHours, maxHours,
                "校验通过：第 " + weekNo + " 周跨岗累计 " + Num.plain(projectedHours) + "h / " + maxHours + "h");
    }

    // ================================================================== 考核

    /** 考核分档：≥90 优秀 1.1；≥60 合格 1.0；其余不合格 0.85 */
    public static GradeInfo gradeFromScore(BigDecimal score) {
        BigDecimal s = Num.orZero(score);
        if (s.compareTo(BigDecimal.valueOf(90)) >= 0) {
            return new GradeInfo("优秀", new BigDecimal("1.1"), true, "success");
        }
        if (s.compareTo(BigDecimal.valueOf(60)) >= 0) {
            return new GradeInfo("合格", new BigDecimal("1.0"), true, "blue");
        }
        return new GradeInfo("不合格", new BigDecimal("0.85"), false, "error");
    }

    // ================================================================== 薪酬与预算联动（P0 新增，见设计文档第 7 节）

    /** 薪酬金额 = 工时 × 时薪 × 考核系数，保留两位小数 */
    public static BigDecimal calcPayrollAmount(BigDecimal totalHours, BigDecimal hourlyRate, BigDecimal coefficient) {
        return Num.money(Num.orZero(totalHours).multiply(Num.orZero(hourlyRate)).multiply(Num.orZero(coefficient)));
    }

    /** 岗位剩余预留 = reservedAmount − 已计入的薪酬（不低于 0） */
    public static BigDecimal reserveRemaining(BigDecimal reservedAmount, BigDecimal committedAmount) {
        return Num.orZero(reservedAmount).subtract(Num.orZero(committedAmount)).max(BigDecimal.ZERO);
    }

    /**
     * 核算一笔薪酬前的预算校验：金额（含考核系数）必须 ≤ 岗位剩余预留 + 经费可用额。
     *
     * @param committedOthers 该岗位下其他已发放、待发放薪酬之和（不含本笔）
     */
    public static PayrollBudgetCheck checkPayrollBudget(BigDecimal amount, BigDecimal reservedAmount,
                                                        BigDecimal committedOthers, Budget budget) {
        return checkPayrollBudget(amount, reservedAmount, committedOthers, budget, BigDecimal.ZERO);
    }

    /**
     * 同上，并扣除同一经费池中其他待发放薪酬已占用的“超出岗位预留部分”。
     * 这部分在发放前不会进入 used，也不在 frozen 里；不扣掉的话，多笔超预留薪酬核算时会各自通过。
     *
     * @param unpaidExcessOthers 同经费池其他待发放薪酬超出各自岗位预留的金额之和
     */
    public static PayrollBudgetCheck checkPayrollBudget(BigDecimal amount, BigDecimal reservedAmount,
                                                        BigDecimal committedOthers, Budget budget,
                                                        BigDecimal unpaidExcessOthers) {
        BigDecimal reserve = reserveRemaining(reservedAmount, committedOthers);
        BigDecimal available = getBudgetAvailable(budget).subtract(Num.orZero(unpaidExcessOthers));
        boolean ok = budget != null && amount.compareTo(reserve.add(available)) <= 0;
        String reason = ok ? "" : budget == null ? "关联经费池不存在"
                : "核算金额 ¥" + Num.locale(amount) + " 超出岗位剩余预留 ¥" + Num.locale(reserve)
                + " 与经费可用额 ¥" + Num.locale(available) + " 之和";
        return new PayrollBudgetCheck(ok, amount, reserve, available, reason);
    }

    /**
     * 发放时应从经费冻结额中扣减的部分 = min(金额, 发放前该岗位剩余预留)，保证不会扣到别的岗位的预留。
     *
     * @param paidBefore 该岗位此前已发放的薪酬之和
     */
    public static BigDecimal payoutFrozenRelease(BigDecimal amount, BigDecimal reservedAmount, BigDecimal paidBefore) {
        return amount.min(reserveRemaining(reservedAmount, paidBefore));
    }

    // ================================================================== 内部工具

    /** 等同 JS 的 Number(v) || fallback：null、0、无法解析都取 fallback */
    private static int numberOr(Object v, int fallback) {
        BigDecimal d = Num.toDecimal(v);
        if (d == null || d.signum() == 0) {
            return fallback;
        }
        return d.intValue();
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isEmpty();
    }
}
