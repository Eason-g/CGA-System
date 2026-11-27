package edu.cga.service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import edu.cga.common.BiText;
import edu.cga.common.BizException;
import edu.cga.common.IdGenerator;
import edu.cga.common.Num;
import edu.cga.common.TimeService;
import edu.cga.domain.entity.Attachment;
import edu.cga.domain.entity.Contract;
import edu.cga.domain.entity.Job;
import edu.cga.domain.entity.JobApplication;
import edu.cga.domain.entity.User;
import edu.cga.domain.mapper.ContractMapper;
import edu.cga.domain.mapper.JobApplicationMapper;
import edu.cga.domain.mapper.JobMapper;
import edu.cga.domain.mapper.UserMapper;
import edu.cga.rule.RuleEngine;
import edu.cga.scope.DataScope;
import edu.cga.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 岗位申请：投递（submitApplication）、国际生办特批（reviewSpecialApproval）、
 * 安排面试（scheduleInterview）、评分与录用（decideApplication）。
 * <p>
 * 涉及工时累计的操作都先锁学生行（录用还锁岗位行），保证并发下累计工时检查基于最新合同。
 */
@Service
public class ApplicationService {

    private final JobApplicationMapper applicationMapper;
    private final JobMapper jobMapper;
    private final UserMapper userMapper;
    private final ContractMapper contractMapper;
    private final DataScope scope;
    private final ConfigService configService;
    private final AuditService audit;
    private final IdGenerator ids;
    private final TimeService time;
    private final AttachmentService attachmentService;

    public ApplicationService(JobApplicationMapper applicationMapper, JobMapper jobMapper, UserMapper userMapper,
                              ContractMapper contractMapper, DataScope scope, ConfigService configService,
                              AuditService audit, IdGenerator ids, TimeService time,
                              AttachmentService attachmentService) {
        this.attachmentService = attachmentService;
        this.applicationMapper = applicationMapper;
        this.jobMapper = jobMapper;
        this.userMapper = userMapper;
        this.contractMapper = contractMapper;
        this.scope = scope;
        this.configService = configService;
        this.audit = audit;
        this.ids = ids;
        this.time = time;
    }

    // ------------------------------------------------------------------ submitApplication

    /**
     * 学生本人投递。以库中学籍为准（签证、工时等字段不信任前端），校验顺序与 mock 一致：
     * 已申请 400 → 名额已满 400 → 合规未通过且未走特批 403 → 不可特批 403 → 特批理由不足 400。
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public JobApplication submit(String jobId, String statement, String resumeFileName, boolean isSpecialApproval,
                                 String specialReason, CurrentUser operator) {
        return submit(jobId, statement, resumeFileName, isSpecialApproval, specialReason, null, operator);
    }

    /** 同上，并关联本人上传的附件（P1 第三批：简历与其他材料） */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public JobApplication submit(String jobId, String statement, String resumeFileName, boolean isSpecialApproval,
                                 String specialReason, List<String> attachmentIds, CurrentUser operator) {
        Job job = scope.requireVisible(jobMapper, scope.jobs(operator), jobId, "岗位不存在");
        // 锁学生行：同一学生的申请与录用、工时填报串行执行
        User student = userMapper.lockById(operator.id());
        if (student == null || !CurrentUser.STUDENT.equals(student.getRole())) {
            throw BizException.forbidden("仅学生本人可提交岗位申请");
        }
        if (!"PUBLISHED".equals(job.getStatus())) {
            throw BizException.badRequest("该岗位未发布或已关闭，不能申请",
                    "This position is not published or has been closed; applications are not accepted");
        }

        List<JobApplication> myApps = applicationMapper.selectByStudent(student.getId());
        List<Contract> myContracts = contractMapper.selectAllocatingByStudent(student.getId());
        RuleEngine.ApplicationCheck check = RuleEngine.validateStudentApplication(student, job, myApps, myContracts,
                configService.globalRulesForWrite(), time.today());
        if (check.alreadyApplied()) {
            throw new BizException(400, "您已申请过该岗位，请勿重复投递",
                    "You have already applied for this position; please do not apply again", check);
        }
        if (check.quotaFull()) {
            throw new BizException(400, "该岗位名额已招满", "This position's quota is full", check);
        }
        if (!check.passed() && !isSpecialApproval) {
            throw new BizException(403, "合规前置校验未通过，系统已拦截提交",
                    "Pre-application compliance checks failed; the submission has been blocked", check);
        }
        if (isSpecialApproval && !check.canSpecialApprove()) {
            throw new BizException(403, "不能发起特批：" + check.specialBlockedReason(),
                    "Cannot request special approval: " + check.specialBlockedReasonEn(), check);
        }
        if (isSpecialApproval && (specialReason == null || specialReason.trim().length() < 6)) {
            throw new BizException(400, "发起特批申请必须填写不少于 6 个字的特批理由",
                    "A special approval request must include a reason of at least 6 characters", check);
        }
        List<Attachment> attachments = attachmentService.requireOwned(attachmentIds, student.getId());
        if (resumeFileName == null || resumeFileName.isBlank()) {
            // 附了简历的，简历文件名取上传的原文件名
            resumeFileName = attachments.stream().filter(a -> Attachment.RESUME.equals(a.getKind()))
                    .map(Attachment::getOriginalName).findFirst().orElse(null);
        }

        JobApplication app = new JobApplication();
        app.setId(ids.next("APP"));
        app.setJobId(job.getId());
        app.setStudentId(student.getId());
        app.setStatus(isSpecialApproval ? "PENDING_SPECIAL_APPROVAL" : "SUBMITTED");
        app.setAppliedAt(time.now());
        app.setStatement(JobService.blankTo(statement, "已认真阅读岗位职责要求，申请加入团队。"));
        app.setResumeFileName(JobService.blankTo(resumeFileName, student.getName() + "_个人简历.pdf"));
        app.setAttachmentIds(attachments.stream().map(Attachment::getId).toList());
        // projectedHours 记录申请时的累计工时，特批通过后录用复核以此为上限
        Map<String, Object> ccr = new LinkedHashMap<>();
        ccr.put("passed", check.passed());
        ccr.put("checks", check.checks());
        ccr.put("currentHours", check.currentHours());
        ccr.put("projectedHours", check.projectedHours());
        ccr.put("maxHours", check.maxHours());
        if (!check.hints().isEmpty()) {
            ccr.put("hints", check.hints());
        }
        app.setComplianceCheckResult(ccr);
        app.setIsSpecialApproval(isSpecialApproval);
        app.setSpecialReason(isSpecialApproval ? specialReason.trim() : "");
        app.setSpecialStatus(isSpecialApproval ? "PENDING" : null);
        app.setInterviewInfo(null);
        app.setScore(null);
        app.setMentorComment("");
        applicationMapper.insert(app);

        audit.log("APPLICATION", app.getId(), operator,
                isSpecialApproval ? "发起国际生办特批申请" : "提交岗位申请",
                isSpecialApproval ? "触发硬校验拦截，特批理由：" + app.getSpecialReason()
                        : "通过合规校验并申请「" + job.getTitle() + "」",
                isSpecialApproval ? "合规拦截" : "未申请", app.getStatus());
        return app;
    }

    // ------------------------------------------------------------------ reviewSpecialApproval

    /** 国际生办、校级管理员审核特批单；申请须为 PENDING_SPECIAL_APPROVAL */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public JobApplication reviewSpecial(String applicationId, String decision, String opinion, CurrentUser operator) {
        JobApplication visible = scope.requireVisible(applicationMapper, scope.applications(operator), applicationId, "申请单不存在");
        User student = userMapper.lockById(visible.getStudentId());
        Job job = jobMapper.lockById(visible.getJobId());
        JobApplication app = applicationMapper.lockById(applicationId);
        if (!"PENDING_SPECIAL_APPROVAL".equals(app.getStatus())) {
            throw BizException.badRequest("该申请不处于待特批状态",
                    "This application is not awaiting special approval");
        }
        boolean approve = "APPROVE".equals(decision);
        BiText enrollment = RuleEngine.enrollmentText(student);
        if (approve && enrollment != null) {
            throw BizException.forbidden(enrollment);
        }
        if (approve && Boolean.TRUE.equals(student.getIsInternational())) {
            if (!RuleEngine.allowsInternationalEmployment(job)) {
                throw BizException.forbidden("该岗位未完成国际生会签，不可特批",
                        "This position has not completed International Office co-signing; special approval is not allowed");
            }
            BiText eligibility = RuleEngine.workingEligibilityText(student, time.today());
            if (eligibility != null) {
                throw BizException.forbidden(eligibility);
            }
        }
        String beforeStatus = app.getStatus();
        app.setStatus(approve ? "SPECIAL_APPROVED" : "SPECIAL_REJECTED");
        app.setSpecialStatus(approve ? "APPROVED" : "REJECTED");
        applicationMapper.updateById(app);

        String jobTitle = job != null ? job.getTitle() : "";
        audit.notify(app.getStudentId(), AuditService.CHANNEL_MAIL,
                "特批申请" + (approve ? "已批准" : "未通过") + "：" + jobTitle,
                JobService.blankTo(opinion, approve ? "国际合作交流处已批准您的特批申请，单据已进入导师筛选环节。" : "您的特批申请未获批准。"));
        audit.log("APPLICATION", app.getId(), operator, approve ? "特批申请审核通过（留痕）" : "特批申请驳回（留痕）",
                JobService.blankTo(opinion, "已完成合规复核"), beforeStatus, app.getStatus());
        return app;
    }

    // ------------------------------------------------------------------ scheduleInterview

    /** 岗位导师本人、校级管理员安排面试；状态须为 SUBMITTED / SPECIAL_APPROVED */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public JobApplication scheduleInterview(String applicationId, String timeText, String location, String note,
                                            CurrentUser operator) {
        scope.requireVisible(applicationMapper, scope.applications(operator), applicationId, "申请单不存在");
        JobApplication app = applicationMapper.lockById(applicationId);
        Job job = jobMapper.selectById(app.getJobId());
        if (job == null) {
            throw BizException.notFound("关联岗位不存在");
        }
        requireJobMentor(job, operator);
        if (!"SUBMITTED".equals(app.getStatus()) && !"SPECIAL_APPROVED".equals(app.getStatus())) {
            throw BizException.badRequest("申请当前状态为「" + statusLabel(app.getStatus()) + "」，不能安排面试");
        }

        String beforeStatus = app.getStatus();
        app.setStatus("INTERVIEW_SCHEDULED");
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("time", timeText);
        info.put("location", location);
        info.put("note", note);
        app.setInterviewInfo(info);
        applicationMapper.updateById(app);

        audit.notify(app.getStudentId(), AuditService.CHANNEL_MAIL, "面试邀请：" + job.getTitle(),
                "面试时间：" + timeText + "，地点：" + location + "。备注：" + JobService.blankTo(note, "请准时参加"));
        audit.log("APPLICATION", app.getId(), operator, "安排面试并发送通知",
                "时间：" + timeText + "，地点：" + location, beforeStatus, "INTERVIEW_SCHEDULED");
        return app;
    }

    // ------------------------------------------------------------------ decideApplication

    /**
     * 岗位导师本人、校级管理员评分并决定录用或不录用。
     * 录用：锁学生行 → 锁岗位行 → 锁申请行（顺序固定），再按最新合同调用 validateHire 复核，
     * 通过后才修改数据并生成待签合同，避免拦截时留下半截状态。
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public JobApplication decide(String applicationId, int score, String decision, String comment,
                                 CurrentUser operator) {
        JobApplication visible = scope.requireVisible(applicationMapper, scope.applications(operator), applicationId,
                "申请单不存在");
        Job visibleJob = jobMapper.selectById(visible.getJobId());
        if (visibleJob == null) {
            throw BizException.notFound("关联岗位不存在");
        }
        requireJobMentor(visibleJob, operator);

        User student = userMapper.lockById(visible.getStudentId());
        if (student == null) {
            throw BizException.notFound("申请人不存在");
        }
        Job job = jobMapper.lockById(visible.getJobId());
        JobApplication app = applicationMapper.lockById(applicationId);

        boolean hire = "HIRE".equals(decision);
        RuleEngine.HireCheck hireCheck = null;
        // 聘期从下周一开始，持续岗位约定的周数；国际生签证有效期必须覆盖到结束日
        LocalDate startDate = RuleEngine.contractStartFrom(time.today());
        LocalDate endDate = RuleEngine.predictContractEnd(time.today(), Num.orZero(job.getWeeks()));
        if (hire) {
            List<Contract> contracts = contractMapper.selectAllocatingByStudent(student.getId());
            hireCheck = RuleEngine.validateHire(app, job, student, contracts, configService.globalRulesForWrite(), endDate);
            if (!hireCheck.ok()) {
                throw new BizException(403, String.join("；", hireCheck.errors()),
                        String.join("; ", hireCheck.errorsEn()), hireCheck);
            }
        } else if (!RuleEngine.HIREABLE_APP_STATUSES.contains(app.getStatus())) {
            throw BizException.badRequest("申请当前状态为「" + statusLabel(app.getStatus()) + "」，不能更改选拔结果");
        }

        String beforeStatus = app.getStatus();
        app.setScore(score);
        app.setMentorComment(comment == null ? "" : comment);

        if (hire) {
            app.setStatus("HIRED");
            applicationMapper.updateById(app);
            job.setHiredCount(Num.orZero(job.getHiredCount()) + 1);
            jobMapper.updateById(job);

            Contract contract = new Contract();
            contract.setId(ids.next("CON"));
            contract.setApplicationId(app.getId());
            contract.setStudentId(app.getStudentId());
            contract.setJobId(job.getId());
            contract.setDeptId(job.getDeptId());
            contract.setStartDate(startDate);
            contract.setEndDate(endDate);
            contract.setWeeklyHours(job.getWeeklyHours());
            contract.setHourlyRate(job.getHourlyRate());
            contract.setStatus("PENDING_SIGN");
            contract.setComplianceAgreed(false);
            contractMapper.insert(contract);

            audit.notify(app.getStudentId(), AuditService.CHANNEL_MAIL, "录用通知：" + job.getTitle(),
                    "恭喜您通过选拔（面试评分 " + score + " 分）！系统已生成电子聘用协议（" + contract.getId()
                            + "），请尽快前往「工时与合同」手写签名确认。");
            audit.log("APPLICATION", app.getId(), operator, "评分并确认录用",
                    "面试评分 " + score + " 分，评语：" + JobService.blankTo(comment, "同意录用") + "；自动生成电子协议 "
                            + contract.getId() + "；录用复核跨岗累计 " + hireCheck.projectedHours() + "h/"
                            + hireCheck.maxHours() + "h" + (hireCheck.hoursOverridden() ? "（超限部分依据国际生办特批放行）" : ""),
                    beforeStatus, "HIRED");
        } else {
            app.setStatus("REJECTED");
            applicationMapper.updateById(app);
            audit.notify(app.getStudentId(), AuditService.CHANNEL_INBOX, "申请结果通知：" + job.getTitle(),
                    "感谢您的投递，经综合评估本次未能录用。评语：" + JobService.blankTo(comment, "名额有限，欢迎关注其他岗位"));
            audit.log("APPLICATION", app.getId(), operator, "选拔未录用",
                    "评分 " + score + " 分，原因：" + JobService.blankTo(comment, "未通过选拔"), beforeStatus, "REJECTED");
        }
        return app;
    }

    /** 导师只能处理自己发布的岗位（校级管理员不限） */
    private static void requireJobMentor(Job job, CurrentUser operator) {
        if (operator.is(CurrentUser.MENTOR) && !operator.id().equals(job.getMentorId())) {
            throw BizException.forbidden("仅岗位导师本人可处理该申请");
        }
    }

    private static String statusLabel(String status) {
        return RuleEngine.APP_STATUS_LABELS.getOrDefault(status, status);
    }
}
