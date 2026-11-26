package edu.cga;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import edu.cga.domain.entity.Budget;
import edu.cga.domain.entity.Contract;
import edu.cga.domain.entity.Job;
import edu.cga.domain.entity.JobApplication;
import edu.cga.domain.entity.Payroll;
import edu.cga.domain.entity.Timesheet;
import edu.cga.domain.entity.User;
import edu.cga.domain.mapper.AuditLogMapper;
import edu.cga.domain.mapper.BudgetMapper;
import edu.cga.domain.mapper.ContractMapper;
import edu.cga.domain.mapper.JobApplicationMapper;
import edu.cga.domain.mapper.JobMapper;
import edu.cga.domain.mapper.PayrollMapper;
import edu.cga.domain.mapper.TimesheetMapper;
import edu.cga.domain.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 写接口集成测试基类：在 IntegrationTestBase 之上提供直接落库的测试数据构造方法。
 * 构造的数据 ID 都以 T_ 开头，与种子数据区分。
 */
public abstract class WriteApiTestBase extends IntegrationTestBase {

    @Autowired
    protected UserMapper userMapper;
    @Autowired
    protected JobMapper jobMapper;
    @Autowired
    protected BudgetMapper budgetMapper;
    @Autowired
    protected JobApplicationMapper applicationMapper;
    @Autowired
    protected ContractMapper contractMapper;
    @Autowired
    protected TimesheetMapper timesheetMapper;
    @Autowired
    protected PayrollMapper payrollMapper;
    @Autowired
    protected AuditLogMapper auditLogMapper;

    /** 新建学生（演示密码可登录，登录名 = id 小写） */
    protected User createStudent(String id, String deptId, boolean intl, boolean visaValid) {
        User u = new User();
        u.setId(id);
        u.setUsername(id.toLowerCase());
        u.setName("测试学生" + id);
        u.setRole("STUDENT");
        u.setRoleLabel("测试学生");
        u.setPortal("student");
        u.setPreset(false);
        u.setDeptId(deptId);
        u.setIsInternational(intl);
        u.setVisaType(intl ? "X1学习居留许可" : null);
        u.setVisaValid(visaValid);
        u.setVisaExpiry(intl ? LocalDate.of(2027, 7, 31) : null);
        u.setLanguageCert("IELTS 7.0");
        u.setLanguageLevel(new BigDecimal("7.0"));
        u.setLanguageValid(true);
        u.setMaxWeeklyHours(20);
        u.setSkills(new ArrayList<>());
        u.setEmail(id.toLowerCase() + "@univ.edu.cn");
        userMapper.insert(u);
        databaseInitializer.initDemoPasswords();
        return u;
    }

    /** 新建一个已发布岗位（不经过审批流，供申请/录用场景使用） */
    protected Job createPublishedJob(String id, String mentorId, String deptId, String budgetId, int weeklyHours,
                                     boolean allowIntl) {
        Job j = new Job();
        j.setId(id);
        j.setType("RA");
        j.setTitle("测试岗位 " + id);
        j.setDeptId(deptId);
        j.setMentorId(mentorId);
        j.setBudgetId(budgetId);
        j.setQuota(2);
        j.setHiredCount(0);
        j.setHourlyRate(new BigDecimal("40"));
        j.setWeeklyHours(weeklyHours);
        j.setWeeks(16);
        j.setAllowInternational(allowIntl);
        j.setStatus("PUBLISHED");
        j.setWorkflowInstanceId("WF_" + id);
        j.setWorkflowSteps(List.of("MENTOR_SUBMIT", "DEPT_REVIEW", "PUBLISHED"));
        j.setCurrentStepIndex(2);
        j.setDeadline(LocalDate.of(2026, 12, 31));
        j.setCreatedAt(LocalDateTime.of(2026, 11, 1, 9, 0));
        j.setSkills(new ArrayList<>());
        j.setDescription("");
        j.setExtraFields(new LinkedHashMap<>());
        j.setReservedAmount(BigDecimal.ZERO);
        jobMapper.insert(j);
        return j;
    }

    protected JobApplication createApplication(String id, String jobId, String studentId, String status) {
        JobApplication a = new JobApplication();
        a.setId(id);
        a.setJobId(jobId);
        a.setStudentId(studentId);
        a.setStatus(status);
        a.setAppliedAt(LocalDateTime.of(2026, 11, 10, 9, 0));
        a.setComplianceCheckResult(Map.of("passed", true));
        a.setIsSpecialApproval(false);
        a.setSpecialReason("");
        a.setMentorComment("");
        applicationMapper.insert(a);
        return a;
    }

    protected Budget createBudget(String id, String deptId, String total, String used, String frozen) {
        Budget b = new Budget();
        b.setId(id);
        b.setCode("TEST-" + id);
        b.setSourceType("RA_PROJECT");
        b.setSourceLabel("科研项目经费");
        b.setDeptId(deptId);
        b.setName("测试经费 " + id);
        b.setTotalAmount(new BigDecimal(total));
        b.setUsedAmount(new BigDecimal(used));
        b.setFrozenAmount(new BigDecimal(frozen));
        budgetMapper.insert(b);
        return b;
    }

    protected Contract createActiveContract(String id, String studentId, Job job) {
        Contract c = new Contract();
        c.setId(id);
        c.setStudentId(studentId);
        c.setJobId(job.getId());
        c.setDeptId(job.getDeptId());
        c.setStartDate(LocalDate.of(2026, 11, 2));
        c.setEndDate(LocalDate.of(2027, 2, 22));
        c.setWeeklyHours(job.getWeeklyHours());
        c.setHourlyRate(job.getHourlyRate());
        c.setStatus("ACTIVE");
        c.setComplianceAgreed(true);
        contractMapper.insert(c);
        return c;
    }

    protected Timesheet createApprovedTimesheet(String id, Contract c, int weekNo, String month, String hours) {
        Timesheet t = new Timesheet();
        t.setId(id);
        t.setContractId(c.getId());
        t.setStudentId(c.getStudentId());
        t.setJobId(c.getJobId());
        t.setDeptId(c.getDeptId());
        t.setWeekNo(weekNo);
        t.setMonth(month);
        t.setHours(new BigDecimal(hours));
        t.setWorkLog("测试工作记录");
        t.setStatus("APPROVED");
        t.setSubmittedAt(LocalDateTime.of(2026, 11, 13, 18, 0));
        t.setMentorComment("");
        timesheetMapper.insert(t);
        return t;
    }

    /** 新建一张薪酬单（金额直接给定，时薪 40） */
    protected Payroll createPayroll(String id, Contract c, Job job, String month, String amount, String status) {
        Payroll p = new Payroll();
        p.setId(id);
        p.setStudentId(c.getStudentId());
        p.setContractId(c.getId());
        p.setJobId(job.getId());
        p.setDeptId(job.getDeptId());
        p.setMonth(month);
        p.setTotalHours(new BigDecimal(amount).divide(new BigDecimal("40"), 1, java.math.RoundingMode.HALF_UP));
        p.setHourlyRate(new BigDecimal("40"));
        p.setCoefficient(BigDecimal.ONE);
        p.setAmount(new BigDecimal(amount));
        p.setBudgetId(job.getBudgetId());
        p.setSourceType("RA_PROJECT");
        p.setFinanceStatus(status);
        p.setCalculatedAt(LocalDateTime.of(2026, 11, 1, 10, 0));
        payrollMapper.insert(p);
        return p;
    }

    protected Budget budget(String id) {
        return budgetMapper.selectById(id);
    }

    protected long auditCount() {
        return auditLogMapper.selectCount(null);
    }
}
