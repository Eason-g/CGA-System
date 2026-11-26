package edu.cga.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import edu.cga.common.BizException;
import edu.cga.common.IdGenerator;
import edu.cga.common.Num;
import edu.cga.common.TimeService;
import edu.cga.domain.entity.Budget;
import edu.cga.domain.entity.Contract;
import edu.cga.domain.entity.Evaluation;
import edu.cga.domain.entity.Job;
import edu.cga.domain.entity.Payroll;
import edu.cga.domain.mapper.BudgetMapper;
import edu.cga.domain.mapper.ContractMapper;
import edu.cga.domain.mapper.EvaluationMapper;
import edu.cga.domain.mapper.JobMapper;
import edu.cga.domain.mapper.PayrollMapper;
import edu.cga.rule.RuleEngine;
import edu.cga.scope.DataScope;
import edu.cga.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 周期考核（submitEvaluation）：按配置中心的考核模板加权计分，并把新系数应用到该合同未发放的薪酬上。
 */
@Service
public class EvaluationService {

    /** 未填某维度分数时的默认分（同 mock 的 ?? 85） */
    private static final BigDecimal DEFAULT_DIMENSION_SCORE = BigDecimal.valueOf(85);

    /** 服务对象评价对应的考核维度 key（TA/RA/GA 模板中均为 service） */
    private static final String SERVICE_DIMENSION = "service";

    /** service 维度默认值来源（P2 C.1）：服务对象评价 / 课程评教 */
    public static final String SOURCE_FEEDBACK = "FEEDBACK";
    public static final String SOURCE_TEACHING_EVAL = "TEACHING_EVAL";

    private final EvaluationMapper evaluationMapper;
    private final ContractMapper contractMapper;
    private final JobMapper jobMapper;
    private final PayrollMapper payrollMapper;
    private final BudgetMapper budgetMapper;
    private final DataScope scope;
    private final ConfigService configService;
    private final AuditService audit;
    private final IdGenerator ids;
    private final TimeService time;
    private final FeedbackService feedbackService;
    private final TeachingEvalService teachingEvalService;

    public EvaluationService(EvaluationMapper evaluationMapper, ContractMapper contractMapper, JobMapper jobMapper,
                             PayrollMapper payrollMapper, BudgetMapper budgetMapper, DataScope scope,
                             ConfigService configService, AuditService audit, IdGenerator ids, TimeService time,
                             FeedbackService feedbackService, TeachingEvalService teachingEvalService) {
        this.feedbackService = feedbackService;
        this.teachingEvalService = teachingEvalService;
        this.evaluationMapper = evaluationMapper;
        this.contractMapper = contractMapper;
        this.jobMapper = jobMapper;
        this.payrollMapper = payrollMapper;
        this.budgetMapper = budgetMapper;
        this.scope = scope;
        this.configService = configService;
        this.audit = audit;
        this.ids = ids;
        this.time = time;
    }

    /** 考核结果：evaluation 为返回给前端的考核单，message 可能附带“薪酬保留原金额”的说明 */
    public record EvaluationResult(Evaluation evaluation, String message) {
    }

    /**
     * 岗位导师本人、本院系管理员、校级管理员可提交。
     * 若该合同有未发放的薪酬，按新系数重算金额；金额上升且超出“岗位剩余预留 + 经费可用额”时保留原金额并在 message 里说明。
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public EvaluationResult submit(String contractId, String period, Map<String, Object> dimensionScores,
                                   String comment, Map<String, Object> courseEvalSync, CurrentUser operator) {
        return submit(contractId, period, dimensionScores, comment, courseEvalSync, null, operator);
    }

    /**
     * 同上。P1：该合同有服务对象评价时，service 维度默认取“平均星级 × 20”（未传该维度分数即用默认值）；
     * 传入的分数与默认值不同时必须填写 serviceOverrideReason，理由写进审计日志。
     * <p>
     * courseEvalSync 只作为“导师确认同步评教”的旧式开关：不为空即等同 syncTeachingEval=true，
     * 其中的数字一律忽略，由后端重新从评教系统拉取（P2 C.1）。
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public EvaluationResult submit(String contractId, String period, Map<String, Object> dimensionScores,
                                   String comment, Map<String, Object> courseEvalSync, String serviceOverrideReason,
                                   CurrentUser operator) {
        return submit(contractId, period, dimensionScores, comment, courseEvalSync != null, null,
                serviceOverrideReason, operator);
    }

    /**
     * 同上（P2 C.1 评教联动）。
     *
     * @param syncTeachingEval     导师已确认同步评教结果：后端按合同所属课程重新拉取，写入 courseEvalSync；
     *                             该课程没有评教数据时 400
     * @param serviceDefaultSource service 维度默认值来源：FEEDBACK（服务对象评价）/ TEACHING_EVAL（课程评教）；
     *                             为空时有服务对象评价取它，否则取已同步的评教结果，都没有则不设默认值
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @SuppressWarnings("unchecked")
    public EvaluationResult submit(String contractId, String period, Map<String, Object> dimensionScores,
                                   String comment, boolean syncTeachingEval, String serviceDefaultSource,
                                   String serviceOverrideReason, CurrentUser operator) {
        Contract con = scope.requireVisible(contractMapper, scope.contracts(operator), contractId, "聘用协议不存在");
        Job job = jobMapper.selectById(con.getJobId());
        if (job == null) {
            throw BizException.notFound("关联岗位不存在");
        }
        if (operator.is(CurrentUser.MENTOR) && !operator.id().equals(job.getMentorId())) {
            throw BizException.forbidden("仅岗位导师本人可提交考核");
        }

        Object template = configService.evaluationTemplates().get(job.getType());
        List<Map<String, Object>> dims = template instanceof List<?> l ? (List<Map<String, Object>>) l : List.of();
        Map<String, Object> scores = dimensionScores == null ? Map.of() : dimensionScores;
        FeedbackService.FeedbackStats feedback = feedbackService.stats(con.getId());
        BigDecimal feedbackScore = feedback.serviceScore();

        // 评教联动：只信任后端从评教系统拉取的数据
        String source = normalizeSource(serviceDefaultSource);
        TeachingEvalService.Lookup teaching = null;
        if (syncTeachingEval || SOURCE_TEACHING_EVAL.equals(source)) {
            teaching = teachingEvalService.lookup(con, job);
            if (!teaching.available()) {
                throw BizException.badRequest(teaching.message() + "，无法同步评教结果");
            }
        }
        BigDecimal teachingScore = teaching == null ? null : teaching.evaluation().serviceScore();
        if (SOURCE_FEEDBACK.equals(source) && feedbackScore == null) {
            throw BizException.badRequest("该聘用协议暂无服务对象评价，不能作为服务维度默认值");
        }
        if (source == null) {
            source = feedbackScore != null ? SOURCE_FEEDBACK : teachingScore != null ? SOURCE_TEACHING_EVAL : null;
        }
        BigDecimal serviceDefault = SOURCE_FEEDBACK.equals(source) ? feedbackScore
                : SOURCE_TEACHING_EVAL.equals(source) ? teachingScore : null;
        String defaultLabel = SOURCE_TEACHING_EVAL.equals(source)
                ? "课程评教折算分（" + teachingEvalService.sourceName() + "）" : "服务对象评价折算分";

        String feedbackNote = "";
        boolean teachingApplied = false;
        List<Map<String, Object>> dimensions = new ArrayList<>();
        BigDecimal weighted = BigDecimal.ZERO;
        for (Map<String, Object> dim : dims) {
            String key = String.valueOf(dim.get("key"));
            Object raw = scores.get(key);
            boolean serviceDim = SERVICE_DIMENSION.equals(key);
            boolean hasDefault = serviceDim && serviceDefault != null;
            BigDecimal defaultScore = hasDefault ? serviceDefault : DEFAULT_DIMENSION_SCORE;
            BigDecimal score = raw == null ? defaultScore : Num.toDecimal(raw);
            if (score == null || score.signum() < 0 || score.compareTo(BigDecimal.valueOf(100)) > 0) {
                throw BizException.badRequest("考核维度「" + dim.get("label") + "」的分数必须在 0–100 之间");
            }
            String overrideReason = null;
            if (serviceDim && feedbackScore != null) {
                feedbackNote += "；服务对象评价 " + feedback.count() + " 条，平均 " + Num.plain(feedback.avgRating())
                        + " 星，折算 " + Num.plain(feedbackScore) + " 分";
            }
            if (serviceDim && teaching != null) {
                feedbackNote += "；" + teachingEvalService.sourceName() + "课程评教 " + teaching.evaluation().responseCount()
                        + " 份，助教题目均分 " + Num.plain(teaching.evaluation().taItemAvg()) + "，折算 "
                        + Num.plain(teachingScore) + " 分";
            }
            if (hasDefault) {
                teachingApplied = SOURCE_TEACHING_EVAL.equals(source);
                feedbackNote += "，默认取" + defaultLabel;
                if (score.compareTo(serviceDefault) != 0) {
                    if (serviceOverrideReason == null || serviceOverrideReason.isBlank()) {
                        throw BizException.badRequest("「" + dim.get("label") + "」默认取" + defaultLabel + " "
                                + Num.plain(serviceDefault) + " 分，修改为 " + Num.plain(score) + " 分须填写修改理由");
                    }
                    overrideReason = serviceOverrideReason.trim();
                    feedbackNote += "，评价人改为 " + Num.plain(score) + " 分，理由：" + overrideReason;
                }
            }
            BigDecimal weight = Num.orZero(Num.toDecimal(dim.get("weight")));
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("key", key);
            d.put("label", dim.get("label"));
            d.put("weight", dim.get("weight"));
            d.put("score", score);
            if (serviceDim && feedbackScore != null) {
                d.put("feedbackCount", feedback.count());
                d.put("feedbackAvg", feedback.avgRating());
                d.put("feedbackScore", feedbackScore);
            }
            if (serviceDim && teaching != null) {
                d.put("teachingEvalScore", teachingScore);
            }
            if (hasDefault) {
                d.put("defaultSource", source);
                d.put("defaultScore", serviceDefault);
                if (overrideReason != null) {
                    d.put("overrideReason", overrideReason);
                }
            }
            dimensions.add(d);
            weighted = weighted.add(score.multiply(weight).divide(BigDecimal.valueOf(100)));
        }
        Map<String, Object> courseEvalSync = teaching == null ? null
                : teachingEvalService.syncRecord(teaching, teachingApplied);
        BigDecimal totalScore = weighted.setScale(1, RoundingMode.HALF_UP);
        RuleEngine.GradeInfo grade = RuleEngine.gradeFromScore(totalScore);

        Evaluation ev = new Evaluation();
        ev.setId(ids.next("EV"));
        ev.setContractId(con.getId());
        ev.setStudentId(con.getStudentId());
        ev.setJobId(con.getJobId());
        ev.setDeptId(con.getDeptId());
        ev.setPeriod(JobService.blankTo(period, time.currentMonth() + " 周期考核"));
        ev.setJobType(job.getType());
        ev.setDimensions(dimensions);
        ev.setTotalScore(totalScore);
        ev.setGrade(grade.grade());
        ev.setCoefficient(grade.coefficient());
        ev.setRenewalEligible(grade.renewalEligible());
        ev.setCourseEvalSync(courseEvalSync);
        ev.setEvaluatorName(operator.name());
        ev.setComment(comment == null ? "" : comment);
        ev.setEvaluatedAt(time.now());
        evaluationMapper.insert(ev);

        // 把新系数应用到未发放的薪酬：锁经费行 → 锁薪酬行（与核算、发放顺序一致）
        String payrollNote = "";
        Budget budget = budgetMapper.lockById(job.getBudgetId());
        Payroll pending = payrollMapper.lockLatestUnpaidByContract(con.getId());
        if (pending != null) {
            BigDecimal oldAmount = Num.orZero(pending.getAmount());
            BigDecimal newAmount = RuleEngine.calcPayrollAmount(pending.getTotalHours(), pending.getHourlyRate(),
                    grade.coefficient());
            BigDecimal committedOthers = payrollMapper.sumCommittedByJob(job.getId(), pending.getId());
            BigDecimal excessOthers = payrollMapper.sumUnpaidExcessByBudget(job.getBudgetId(), pending.getId());
            RuleEngine.PayrollBudgetCheck check = RuleEngine.checkPayrollBudget(newAmount, job.getReservedAmount(),
                    committedOthers, budget, excessOthers);
            if (newAmount.compareTo(oldAmount) > 0 && !check.ok()) {
                payrollNote = "；关联未发放薪酬 " + pending.getId() + " 按新系数将超出预算（" + check.reason()
                        + "），已保留原金额 ¥" + Num.fixed2(oldAmount);
            } else {
                pending.setCoefficient(grade.coefficient());
                pending.setAmount(newAmount);
                payrollMapper.updateById(pending);
            }
        }

        audit.notify(con.getStudentId(), AuditService.CHANNEL_INBOX,
                "周期考核结果公布：" + grade.grade() + "（" + Num.plain(totalScore) + " 分）",
                "岗位「" + job.getTitle() + "」考核分档为「" + grade.grade() + "」，续聘资格："
                        + (grade.renewalEligible() ? "可续聘" : "不予续聘") + "，下周期薪酬系数：" + Num.plain(grade.coefficient())
                        + "x。");
        audit.log("EVALUATION", ev.getId(), operator, "提交周期性考核评价",
                "加权得分 " + Num.plain(totalScore) + " 分，等级「" + grade.grade() + "」，薪酬系数 "
                        + Num.plain(grade.coefficient()) + "x，续聘资格：" + (grade.renewalEligible() ? "是" : "否")
                        + feedbackNote + payrollNote,
                "待考核", grade.grade() + "（" + Num.plain(grade.coefficient()) + "x）");

        String message = "考核已提交：" + grade.grade() + "（" + Num.plain(totalScore) + "分，系数 "
                + Num.plain(grade.coefficient()) + "x）" + payrollNote;
        return new EvaluationResult(ev, message);
    }

    /** 默认值来源：空 → null；FEEDBACK / TEACHING_EVAL（不区分大小写）；其他取值 400 */
    private static String normalizeSource(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String v = s.trim().toUpperCase();
        if (!SOURCE_FEEDBACK.equals(v) && !SOURCE_TEACHING_EVAL.equals(v)) {
            throw BizException.badRequest("服务维度默认值来源只能是服务对象评价（FEEDBACK）或课程评教（TEACHING_EVAL）");
        }
        return v;
    }
}
