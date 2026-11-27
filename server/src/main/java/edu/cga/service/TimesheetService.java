package edu.cga.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import edu.cga.common.BizException;
import edu.cga.common.IdGenerator;
import edu.cga.common.Num;
import edu.cga.common.TimeService;
import edu.cga.domain.entity.Contract;
import edu.cga.domain.entity.Job;
import edu.cga.domain.entity.Timesheet;
import edu.cga.domain.entity.User;
import edu.cga.domain.mapper.ContractMapper;
import edu.cga.domain.mapper.JobMapper;
import edu.cga.domain.mapper.TimesheetMapper;
import edu.cga.domain.mapper.UserMapper;
import edu.cga.rule.RuleEngine;
import edu.cga.scope.DataScope;
import edu.cga.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 周工时：学生填报（submitTimesheet）与导师/院系审核（reviewTimesheet）。
 */
@Service
public class TimesheetService {

    private final TimesheetMapper timesheetMapper;
    private final ContractMapper contractMapper;
    private final JobMapper jobMapper;
    private final UserMapper userMapper;
    private final DataScope scope;
    private final ConfigService configService;
    private final AuditService audit;
    private final IdGenerator ids;
    private final TimeService time;

    public TimesheetService(TimesheetMapper timesheetMapper, ContractMapper contractMapper, JobMapper jobMapper,
                            UserMapper userMapper, DataScope scope, ConfigService configService, AuditService audit,
                            IdGenerator ids, TimeService time) {
        this.timesheetMapper = timesheetMapper;
        this.contractMapper = contractMapper;
        this.jobMapper = jobMapper;
        this.userMapper = userMapper;
        this.scope = scope;
        this.configService = configService;
        this.audit = audit;
        this.ids = ids;
        this.time = time;
    }

    // ------------------------------------------------------------------ submitTimesheet

    /**
     * 学生本人填报，合同须 ACTIVE，周次不能晚于当前教学周。
     * 先锁学生行，再按周跨岗累计校验（validateTimesheetSubmission），超上限拦截（403）。
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Timesheet submit(String contractId, int weekNo, BigDecimal hours, String workLog, String milestoneRef,
                            CurrentUser operator) {
        User student = userMapper.lockById(operator.id());
        if (student == null || !CurrentUser.STUDENT.equals(student.getRole())) {
            throw BizException.forbidden("仅学生本人可填报工时");
        }
        Contract con = scope.requireVisible(contractMapper, scope.contracts(operator), contractId, "有效聘用协议不存在");
        if (!student.getId().equals(con.getStudentId())) {
            throw BizException.notFound("有效聘用协议不存在");
        }
        if (!"ACTIVE".equals(con.getStatus())) {
            throw BizException.badRequest("请先签署电子聘用协议后再填报工时");
        }
        int currentWeek = time.currentWeekNo();
        if (weekNo > currentWeek) {
            throw BizException.badRequest("填报周次不能晚于当前教学周（第 " + currentWeek + " 周）");
        }

        LocalDate weekStart = time.weekStart(weekNo);
        LocalDate workThrough = weekStart.plusDays(6);
        if (workThrough.isAfter(time.today())) {
            workThrough = time.today();
        }
        if (con.getEndDate() != null && con.getEndDate().isBefore(workThrough)) {
            workThrough = con.getEndDate();
        }
        LocalDate workStart = con.getStartDate() != null && con.getStartDate().isAfter(weekStart)
                ? con.getStartDate() : weekStart;
        if (workStart.isAfter(workThrough)) {
            throw BizException.badRequest("填报周次不在合同已履行的聘期内");
        }
        String eligibility = RuleEngine.checkWorkingEligibility(student, workThrough);
        if (eligibility != null) {
            throw BizException.forbidden(eligibility);
        }
        String workMonth = weekStart.format(TimeService.MONTH_FORMAT);
        // 以前的工时用提交月保存。同学期内仍应计入跨岗累计。
        List<Timesheet> weekSheets = timesheetMapper.selectByStudentAndWeek(student.getId(), weekNo).stream()
                .filter(t -> t.getMonth() != null && t.getMonth().compareTo(workMonth) >= 0
                        && t.getMonth().compareTo(time.currentMonth()) <= 0)
                .toList();
        RuleEngine.TimesheetCheck check = RuleEngine.validateTimesheetSubmission(student, weekNo, hours, weekSheets,
                configService.globalRulesForWrite());
        if (!check.ok()) {
            throw new BizException(403, check.message(), check);
        }

        Timesheet ts = new Timesheet();
        ts.setId(ids.next("TS"));
        ts.setContractId(con.getId());
        ts.setStudentId(student.getId());
        ts.setJobId(con.getJobId());
        ts.setDeptId(con.getDeptId());
        ts.setWeekNo(weekNo);
        ts.setMonth(workMonth);
        ts.setHours(hours);
        ts.setWorkLog(workLog.trim());
        ts.setMilestoneRef(milestoneRef == null ? "" : milestoneRef);
        ts.setStatus("PENDING");
        ts.setSubmittedAt(time.now());
        ts.setMentorComment("");
        timesheetMapper.insert(ts);

        audit.log("TIMESHEET", ts.getId(), operator, "填报周工时记录",
                "第 " + weekNo + " 周填报 " + Num.plain(hours) + "h（跨岗累计 " + Num.plain(check.projectedHours()) + "h/"
                        + check.maxHours() + "h）",
                "当周已报 " + Num.plain(check.existingHours()) + "h",
                "待导师审核（当周累计 " + Num.plain(check.projectedHours()) + "h）");
        return ts;
    }

    // ------------------------------------------------------------------ reviewTimesheet

    /** 岗位导师本人、本院系管理员、校级管理员审核；状态须为 PENDING */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Timesheet review(String timesheetId, String decision, String comment, CurrentUser operator) {
        scope.requireVisible(timesheetMapper, scope.timesheets(operator), timesheetId, "工时单不存在");
        Timesheet ts = timesheetMapper.lockById(timesheetId);
        if (operator.is(CurrentUser.MENTOR)) {
            Job job = jobMapper.selectById(ts.getJobId());
            if (job == null || !operator.id().equals(job.getMentorId())) {
                throw BizException.forbidden("仅岗位导师本人可审核该工时单");
            }
        }
        if (!"PENDING".equals(ts.getStatus())) {
            throw BizException.badRequest("该工时单已审核，请勿重复操作");
        }
        boolean approve = "APPROVE".equals(decision);
        String beforeStatus = ts.getStatus();
        ts.setStatus(approve ? "APPROVED" : "REJECTED");
        ts.setMentorComment(JobService.blankTo(comment, approve ? "工时核实无误，同意通过" : "工作记录不完整，请补充后重提"));
        timesheetMapper.updateById(ts);

        audit.log("TIMESHEET", ts.getId(), operator, approve ? "审核通过周工时" : "驳回周工时填报",
                ts.getMentorComment(), beforeStatus, ts.getStatus());
        return ts;
    }
}
