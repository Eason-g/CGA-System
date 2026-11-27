package edu.cga.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import edu.cga.common.BizException;
import edu.cga.common.IdGenerator;
import edu.cga.common.Num;
import edu.cga.domain.entity.Contract;
import edu.cga.domain.entity.Evaluation;
import edu.cga.domain.entity.Job;
import edu.cga.domain.entity.User;
import edu.cga.domain.mapper.ContractMapper;
import edu.cga.domain.mapper.EvaluationMapper;
import edu.cga.domain.mapper.JobMapper;
import edu.cga.domain.mapper.UserMapper;
import edu.cga.rule.RuleEngine;
import edu.cga.scope.DataScope;
import edu.cga.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 续聘（P1 设计 1.4）：POST /api/contracts/{id}/renew。
 * <p>
 * 条件：合同为 ACTIVE、最近一次考核 renewalEligible=true、岗位仍为 PUBLISHED，并通过与录用相同的
 * 跨岗工时复核和国际生签证复核（签证有效期必须覆盖新聘期）。通过后生成新的待签合同：
 * 开始日为原合同结束日次日，周数同岗位（结束日算法与录用一致：开始日 + 周数 × 7 天）；原合同不变。
 * <p>
 * 跨岗工时只累计与新聘期有重叠的生效/待签合同——原合同在新聘期开始前就结束了，不计入；
 * 聘期日期为空的合同保守地视为重叠。加锁顺序与录用一致：学生行 → 岗位行 → 合同行。
 */
@Service
public class RenewalService {

    private final ContractMapper contractMapper;
    private final JobMapper jobMapper;
    private final UserMapper userMapper;
    private final EvaluationMapper evaluationMapper;
    private final DataScope scope;
    private final ConfigService configService;
    private final AuditService audit;
    private final IdGenerator ids;

    public RenewalService(ContractMapper contractMapper, JobMapper jobMapper, UserMapper userMapper,
                          EvaluationMapper evaluationMapper, DataScope scope, ConfigService configService,
                          AuditService audit, IdGenerator ids) {
        this.contractMapper = contractMapper;
        this.jobMapper = jobMapper;
        this.userMapper = userMapper;
        this.evaluationMapper = evaluationMapper;
        this.scope = scope;
        this.configService = configService;
        this.audit = audit;
        this.ids = ids;
    }

    /** 续聘复核结果；被拦截时作为 403 的 data 返回，前端可展示 errors */
    public record RenewalCheck(boolean ok, List<String> errors, String evaluationId, String grade,
                               LocalDate newStartDate, LocalDate newEndDate, int currentHours, int jobHours,
                               int projectedHours, int maxHours, List<String> errorsEn) {
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Contract renew(String contractId, CurrentUser operator) {
        Contract visible = scope.requireVisible(contractMapper, scope.contracts(operator), contractId, "聘用协议不存在");
        Job visibleJob = jobMapper.selectById(visible.getJobId());
        if (visibleJob == null) {
            throw BizException.notFound("关联岗位不存在");
        }
        if (operator.is(CurrentUser.MENTOR) && !operator.id().equals(visibleJob.getMentorId())) {
            throw BizException.forbidden("仅岗位导师本人可发起续聘");
        }

        User student = userMapper.lockById(visible.getStudentId());
        if (student == null) {
            throw BizException.notFound("受聘学生不存在");
        }
        Job job = jobMapper.lockById(visible.getJobId());
        Contract con = contractMapper.lockById(contractId);

        // 状态类条件：400
        if (!"ACTIVE".equals(con.getStatus())) {
            throw BizException.badRequest("只能对生效中的聘用协议发起续聘",
                    "Renewal can only be initiated for an active employment contract");
        }
        if (!"PUBLISHED".equals(job.getStatus())) {
            throw BizException.badRequest("岗位「" + job.getTitle() + "」已不在发布状态，不能续聘");
        }
        if (con.getEndDate() == null) {
            throw BizException.badRequest("原聘用协议没有结束日期，无法计算新聘期");
        }
        LocalDate newStart = con.getEndDate().plusDays(1);
        LocalDate newEnd = newStart.plusWeeks(Math.max(1, Num.orZero(job.getWeeks())));
        List<Contract> allocating = contractMapper.selectAllocatingByStudent(student.getId());
        for (Contract c : allocating) {
            if (c.getJobId().equals(job.getId()) && !c.getId().equals(con.getId()) && c.getStartDate() != null
                    && !c.getStartDate().isBefore(newStart)) {
                throw BizException.badRequest("该学生已有本岗位的续聘协议 " + c.getId() + "，请勿重复发起");
            }
        }

        // 合规类条件：403，一次列出全部原因
        List<String> errors = new ArrayList<>();
        List<String> errorsEn = new ArrayList<>();
        Evaluation latest = evaluationMapper.selectOne(new QueryWrapper<Evaluation>().eq("contract_id", con.getId())
                .orderByDesc("evaluated_at").orderByDesc("id").last("LIMIT 1"));
        if (latest == null) {
            errors.add("该聘用协议还没有周期考核记录，不能续聘");
            errorsEn.add("This contract has no periodic evaluation record yet; cannot renew");
        } else if (!Boolean.TRUE.equals(latest.getRenewalEligible())) {
            errors.add("最近一次考核（" + latest.getPeriod() + "）为「" + latest.getGrade() + "」（"
                    + Num.plain(latest.getTotalScore()) + " 分），不具备续聘资格");
            errorsEn.add("The latest evaluation (" + latest.getPeriod() + ") was \"" + gradeEn(latest.getGrade()) + "\" ("
                    + Num.plain(latest.getTotalScore()) + " points); not eligible for renewal");
        }
        if (RuleEngine.checkEnrollment(student) != null) {
            errors.add("仅在读学生可续聘，当前学籍状态为「" + RuleEngine.enrollmentStatusOf(student) + "」");
            errorsEn.add("Only enrolled students can be renewed; current enrollment status is \""
                    + RuleEngine.enrollmentStatusEn(RuleEngine.enrollmentStatusOf(student)) + "\"");
        }
        if (Boolean.TRUE.equals(student.getIsInternational())) {
            if (!RuleEngine.hasValidVisa(student, null)) {
                errors.add("该国际生无有效学习类居留许可，不能续聘");
                errorsEn.add("This international student has no valid study residence permit; cannot renew");
            } else if (student.getVisaExpiry() == null || student.getVisaExpiry().isBefore(newEnd)) {
                String expiry = student.getVisaExpiry() == null ? "未登记" : student.getVisaExpiry().toString();
                errors.add("该国际生签证（居留许可）有效期至 " + expiry + "，未覆盖新聘期（" + newStart + " 至 " + newEnd
                        + "），不能续聘");
                errorsEn.add("This international student's visa (residence permit) is valid until "
                        + (student.getVisaExpiry() == null ? "not registered" : student.getVisaExpiry().toString())
                        + ", which does not cover the new employment period (" + newStart + " to " + newEnd
                        + "); cannot renew");
            }
            if (!RuleEngine.allowsInternationalEmployment(job)) {
                errors.add("该岗位未面向国际学生开放，不能续聘国际生");
                errorsEn.add("This position is not open to international students; international students cannot be renewed");
            }
        }
        int currentHours = RuleEngine.getStudentAllocatedWeeklyHours(allocating, student.getId(), newStart, newEnd);
        int jobHours = Num.orZero(job.getWeeklyHours());
        int projectedHours = currentHours + jobHours;
        int maxHours = RuleEngine.getMaxWeeklyHours(configService.globalRulesForWrite(), student);
        if (projectedHours > maxHours) {
            errors.add("续聘后新聘期内跨岗累计周工时将达 " + projectedHours + "h（同期已占 " + currentHours + "h + 本岗 "
                    + jobHours + "h），超出上限 " + maxHours + "h/周，系统已拦截续聘");
            errorsEn.add("After renewal, cross-job weekly hours in the new employment period would reach " + projectedHours
                    + "h (concurrently allocated " + currentHours + "h + this position " + jobHours
                    + "h), exceeding the limit of " + maxHours + "h/week; renewal has been blocked");
        }
        RenewalCheck check = new RenewalCheck(errors.isEmpty(), errors, latest == null ? null : latest.getId(),
                latest == null ? null : latest.getGrade(), newStart, newEnd, currentHours, jobHours, projectedHours,
                maxHours, errorsEn);
        if (!errors.isEmpty()) {
            throw new BizException(403, String.join("；", errors), String.join("; ", errorsEn), check);
        }

        Contract renewed = new Contract();
        renewed.setId(ids.next("CON"));
        renewed.setApplicationId(con.getApplicationId());
        renewed.setStudentId(con.getStudentId());
        renewed.setJobId(job.getId());
        renewed.setDeptId(job.getDeptId());
        renewed.setStartDate(newStart);
        renewed.setEndDate(newEnd);
        renewed.setWeeklyHours(job.getWeeklyHours());
        renewed.setHourlyRate(job.getHourlyRate());
        renewed.setStatus("PENDING_SIGN");
        renewed.setComplianceAgreed(false);
        contractMapper.insert(renewed);

        audit.notify(con.getStudentId(), AuditService.CHANNEL_MAIL, "续聘通知：" + job.getTitle(),
                "根据最近一次考核结果（" + latest.getGrade() + "），岗位导师已为您发起续聘，新聘期 " + newStart + " 至 "
                        + newEnd + "。系统已生成电子聘用协议（" + renewed.getId() + "），请前往「工时与合同」签名确认。");
        audit.log("CONTRACT", renewed.getId(), operator, "发起续聘",
                "依据考核 " + latest.getId() + "（" + latest.getGrade() + " " + Num.plain(latest.getTotalScore())
                        + " 分）续聘原协议 " + con.getId() + "，新聘期 " + newStart + " 至 " + newEnd + "；跨岗累计 "
                        + projectedHours + "h/" + maxHours + "h",
                "原协议 " + con.getId() + "（生效中）", "PENDING_SIGN");
        return renewed;
    }

    /** 考核分档的英文（优秀/合格/不合格），其他值原样返回 */
    static String gradeEn(String grade) {
        if (grade == null) {
            return "";
        }
        return switch (grade) {
            case "优秀" -> "Excellent";
            case "合格" -> "Pass";
            case "不合格" -> "Fail";
            default -> grade;
        };
    }
}
