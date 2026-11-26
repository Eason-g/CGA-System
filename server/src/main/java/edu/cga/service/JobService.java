package edu.cga.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import edu.cga.common.BizException;
import edu.cga.common.IdGenerator;
import edu.cga.common.Num;
import edu.cga.common.TimeService;
import edu.cga.domain.entity.Budget;
import edu.cga.domain.entity.Department;
import edu.cga.domain.entity.Job;
import edu.cga.domain.mapper.BudgetMapper;
import edu.cga.domain.mapper.DepartmentMapper;
import edu.cga.domain.mapper.JobMapper;
import edu.cga.integration.research.ResearchProjectService;
import edu.cga.rule.RuleEngine;
import edu.cga.scope.DataScope;
import edu.cga.security.CurrentUser;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 岗位：建岗（createJob）、审批流转（reviewJob）、助研里程碑（toggleMilestone）。
 * <p>
 * 写事务统一使用 READ COMMITTED：先 SELECT … FOR UPDATE 锁行，锁之后的普通查询能读到其他事务刚提交的数据。
 */
@Service
public class JobService {

    /** 流程节点 → 有权审批的角色（SCHOOL_ADMIN 可代审任意节点） */
    private static final Map<String, String> STEP_REVIEWER = Map.of(
            "DEPT_REVIEW", CurrentUser.DEPT_ADMIN,
            "INTL_REVIEW", CurrentUser.INTL_OFFICER,
            "SCHOOL_REVIEW", CurrentUser.SCHOOL_ADMIN);

    private static final Map<String, String> ROLE_NAMES = Map.of(
            CurrentUser.DEPT_ADMIN, "院系管理员",
            CurrentUser.INTL_OFFICER, "国际生办",
            CurrentUser.SCHOOL_ADMIN, "校级管理员");

    /** 审批通过的审计动作，同 mock 的 actionMap */
    private static final Map<String, String> APPROVE_ACTIONS = Map.of(
            CurrentUser.DEPT_ADMIN, "院系初审通过",
            CurrentUser.INTL_OFFICER, "国际生办会签通过",
            CurrentUser.SCHOOL_ADMIN, "校级终审通过");

    /** 未填截止日期时，默认为今天起 30 天 */
    private static final int DEFAULT_DEADLINE_DAYS = 30;

    private final JobMapper jobMapper;
    private final BudgetMapper budgetMapper;
    private final DepartmentMapper departmentMapper;
    private final DataScope scope;
    private final ConfigService configService;
    private final AuditService audit;
    private final IdGenerator ids;
    private final TimeService time;
    private final JobTypeSchemaService jobTypeSchemas;
    /** P2 B.3：RA 岗位绑定科研项目的校验 */
    private final ResearchProjectService researchProjects;

    public JobService(JobMapper jobMapper, BudgetMapper budgetMapper, DepartmentMapper departmentMapper,
                      DataScope scope, ConfigService configService, AuditService audit, IdGenerator ids,
                      TimeService time, JobTypeSchemaService jobTypeSchemas,
                      ResearchProjectService researchProjects) {
        this.jobTypeSchemas = jobTypeSchemas;
        this.researchProjects = researchProjects;
        this.jobMapper = jobMapper;
        this.budgetMapper = budgetMapper;
        this.departmentMapper = departmentMapper;
        this.scope = scope;
        this.configService = configService;
        this.audit = audit;
        this.ids = ids;
        this.time = time;
    }

    /** 建岗表单（字段名同 api.js createJob 的 payload），参数校验注解在 Controller 绑定时生效 */
    public record JobDraft(
            @NotBlank(message = "请选择岗位类型")
            @Pattern(regexp = "TA|RA|GA", message = "岗位类型必须为 TA、RA 或 GA") String type,
            @NotBlank(message = "请填写岗位名称") @Size(max = 128, message = "岗位名称不能超过 128 字") String title,
            @NotBlank(message = "请选择所属院系") String deptId,
            @NotBlank(message = "请选择经费来源账号") String budgetId,
            @NotNull(message = "请填写招聘人数") @Positive(message = "招聘人数必须为正整数") Integer quota,
            @NotNull(message = "请填写时薪") @Positive(message = "时薪必须大于 0")
            @Digits(integer = 8, fraction = 2, message = "时薪最多两位小数") BigDecimal hourlyRate,
            @NotNull(message = "请填写周工时") @Positive(message = "周工时必须为正整数")
            @Max(value = 40, message = "周工时不能超过 40 小时") Integer weeklyHours,
            @NotNull(message = "请填写聘期周数") @Positive(message = "聘期周数必须为正整数") Integer weeks,
            Boolean allowInternational,
            LocalDate deadline,
            List<String> skills,
            String description,
            Map<String, Object> extraFields) {
    }

    // ------------------------------------------------------------------ createJob

    /**
     * 导师/院系管理员只能为本院系建岗，校级管理员不限；经费必须在操作人可见范围内。
     * 锁院系行（名额）和经费行（预算）后做硬校验，通过则把预计成本冻结到经费并记为岗位预留。
     * 预算与名额校验通过后，再按 jobTypeSchemas 校验差异化字段与经费来源（{@link JobTypeSchemaService}）。
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Job createJob(JobDraft d, CurrentUser operator) {
        if (!operator.is(CurrentUser.SCHOOL_ADMIN) && !d.deptId().equals(operator.deptId())) {
            throw BizException.forbidden("只能为本院系发布岗位");
        }
        // 经费不在可见范围内按不存在处理（404）
        scope.requireVisible(budgetMapper, scope.budgets(operator), d.budgetId(), "经费来源账号不存在");

        // 加锁顺序固定：院系 → 经费
        Department dept = departmentMapper.lockById(d.deptId());
        if (dept == null) {
            throw BizException.badRequest("院系不存在");
        }
        Budget budget = budgetMapper.lockById(d.budgetId());
        List<Job> deptJobs = jobMapper.selectByDept(d.deptId());

        RuleEngine.JobForm form = new RuleEngine.JobForm(d.deptId(), d.budgetId(), d.quota(), d.hourlyRate(),
                d.weeklyHours(), d.weeks());
        RuleEngine.JobPublishCheck check = RuleEngine.validateJobPublish(form, budget, dept, deptJobs);
        if (!check.ok()) {
            throw new BizException(400, String.join("；", check.errors()), check);
        }
        // P1：按岗位类型配置校验差异化字段与经费来源，并生成派生字段（languageRequirement、milestones）
        LocalDate deadline = d.deadline() != null ? d.deadline() : time.today().plusDays(DEFAULT_DEADLINE_DAYS);
        Map<String, Object> extraFields = jobTypeSchemas.prepareExtraFields(d.type(), d.extraFields(), budget,
                d.weeks(), deadline);
        // P2 B.3：RA 岗位须绑定科研系统中存在、负责人相符、周期覆盖聘期的项目（不通过 400）
        if ("RA".equals(d.type())) {
            researchProjects.requireValidForJob(String.valueOf(extraFields.getOrDefault("projectCode", "")),
                    d.deptId(), operator, deadline, d.weeks());
        }

        boolean allowIntl = Boolean.TRUE.equals(d.allowInternational());
        List<String> steps = RuleEngine.buildWorkflowStepsForJob(configService.workflows(), d.type(), allowIntl);
        // 第 0 步是导师提交，直接推进到第 1 步
        int nextIndex = Math.min(1, steps.size() - 1);
        String initialStatus = RuleEngine.STEP_TO_JOB_STATUS.getOrDefault(steps.get(nextIndex), "PUBLISHED");

        BigDecimal cost = check.estimatedCost();
        Job job = new Job();
        job.setId(ids.next("JOB"));
        job.setType(d.type());
        job.setTitle(d.title().trim());
        job.setDeptId(d.deptId());
        job.setMentorId(operator.id());
        job.setBudgetId(d.budgetId());
        job.setQuota(d.quota());
        job.setHiredCount(0);
        job.setHourlyRate(d.hourlyRate());
        job.setWeeklyHours(d.weeklyHours());
        job.setWeeks(d.weeks());
        job.setAllowInternational(allowIntl);
        job.setStatus(initialStatus);
        job.setWorkflowInstanceId(ids.next("WF"));
        job.setWorkflowSteps(steps);
        job.setCurrentStepIndex(nextIndex);
        job.setDeadline(deadline);
        job.setCreatedAt(time.now());
        job.setSkills(d.skills() != null ? d.skills() : new ArrayList<>());
        job.setDescription(d.description() != null ? d.description() : "");
        job.setExtraFields(extraFields);
        job.setReservedAmount(cost);

        // 冻结预计预算
        BigDecimal beforeFrozen = Num.orZero(budget.getFrozenAmount());
        budget.setFrozenAmount(beforeFrozen.add(cost));
        budgetMapper.updateById(budget);
        jobMapper.insert(job);

        audit.log("JOB", job.getId(), operator, "提交岗位发布申请",
                "发布 " + job.getType() + " 岗位「" + job.getTitle() + "」，招聘 " + job.getQuota() + " 人，冻结预计预算 ¥"
                        + Num.locale(cost) + "。",
                "冻结经费 ¥" + Num.locale(beforeFrozen),
                initialStatus + "（冻结经费 ¥" + Num.locale(budget.getFrozenAmount()) + "）");
        return job;
    }

    // ------------------------------------------------------------------ reviewJob

    /**
     * 岗位须处于待审状态；只有当前流程节点的角色可审（SCHOOL_ADMIN 可代审任意节点）。
     * 先按数据范围取岗位（不在范围内 404），再判断角色（403）。驳回时释放岗位预留。
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Job reviewJob(String jobId, String decision, String opinion, CurrentUser operator) {
        scope.requireVisible(jobMapper, scope.jobs(operator), jobId, "岗位不存在");
        if (!STEP_REVIEWER.containsValue(operator.role())) {
            throw BizException.forbidden("无权审批岗位");
        }
        Job job = jobMapper.lockById(jobId);
        String stepKey = currentReviewStep(job);
        if (stepKey == null) {
            throw BizException.badRequest("该岗位当前不处于待审批状态");
        }
        String reviewer = STEP_REVIEWER.get(stepKey);
        if (!operator.is(CurrentUser.SCHOOL_ADMIN) && !operator.is(reviewer)) {
            throw BizException.forbidden("当前流程节点为「" + RuleEngine.JOB_STATUS_LABELS.get(job.getStatus())
                    + "」，需由" + ROLE_NAMES.get(reviewer) + "审批");
        }
        // 院系初审只能由本院系管理员审批（数据范围已保证，这里再明确校验一次）
        if (operator.is(CurrentUser.DEPT_ADMIN) && !job.getDeptId().equals(operator.deptId())) {
            throw BizException.notFound("岗位不存在");
        }

        String beforeStatus = job.getStatus();
        if ("REJECT".equals(decision)) {
            job.setStatus("REJECTED");
            // 释放岗位预留：从经费冻结额中扣回 reservedAmount，岗位预留置 0
            BigDecimal reserved = Num.orZero(job.getReservedAmount());
            Budget budget = budgetMapper.lockById(job.getBudgetId());
            if (budget != null) {
                budget.setFrozenAmount(Num.orZero(budget.getFrozenAmount()).subtract(reserved).max(BigDecimal.ZERO));
                budgetMapper.updateById(budget);
            }
            job.setReservedAmount(BigDecimal.ZERO);
            jobMapper.updateById(job);
            audit.log("JOB", job.getId(), operator, "驳回岗位申请",
                    blankTo(opinion, "审核未通过，退回修改并释放冻结预算"), beforeStatus, "REJECTED");
        } else {
            int current = job.getCurrentStepIndex() == null || job.getCurrentStepIndex() == 0
                    ? 1 : job.getCurrentStepIndex();
            int nextIdx = Math.min(current + 1, job.getWorkflowSteps().size() - 1);
            job.setCurrentStepIndex(nextIdx);
            job.setStatus(RuleEngine.STEP_TO_JOB_STATUS.getOrDefault(job.getWorkflowSteps().get(nextIdx), "PUBLISHED"));
            jobMapper.updateById(job);
            audit.log("JOB", job.getId(), operator, APPROVE_ACTIONS.getOrDefault(operator.role(), "审批通过"),
                    blankTo(opinion, "核验无误，同意进入下一流程节点"), beforeStatus, job.getStatus());
        }
        return job;
    }

    /** 待审状态对应的流程节点；不是待审状态返回 null */
    private static String currentReviewStep(Job job) {
        for (Map.Entry<String, String> e : RuleEngine.STEP_TO_JOB_STATUS.entrySet()) {
            if (e.getValue().equals(job.getStatus()) && STEP_REVIEWER.containsKey(e.getKey())) {
                return e.getKey();
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ toggleMilestone

    /** 岗位导师本人、校级管理员可更新助研里程碑 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @SuppressWarnings("unchecked")
    public Map<String, Object> toggleMilestone(String jobId, String milestoneId, CurrentUser operator) {
        scope.requireVisible(jobMapper, scope.jobs(operator), jobId, "岗位不存在");
        Job job = jobMapper.lockById(jobId);
        if (!operator.is(CurrentUser.SCHOOL_ADMIN) && !operator.id().equals(job.getMentorId())) {
            throw BizException.forbidden("仅岗位导师本人可更新里程碑");
        }
        Map<String, Object> extra = job.getExtraFields();
        Object list = extra == null ? null : extra.get("milestones");
        Map<String, Object> ms = null;
        if (list instanceof List<?> items) {
            for (Object item : items) {
                if (item instanceof Map<?, ?> m && milestoneId.equals(String.valueOf(m.get("id")))) {
                    ms = (Map<String, Object>) m;
                }
            }
        }
        if (ms == null) {
            throw BizException.notFound("里程碑不存在");
        }
        boolean done = Boolean.TRUE.equals(ms.get("done"));
        ms.put("done", !done);
        jobMapper.updateById(job);
        audit.log("JOB", job.getId(), operator, "更新助研阶段里程碑状态", "里程碑「" + ms.get("title") + "」",
                done ? "已完成" : "进行中", !done ? "已完成" : "进行中");
        return ms;
    }

    static String blankTo(String s, String fallback) {
        return s == null || s.isBlank() ? fallback : s;
    }
}
