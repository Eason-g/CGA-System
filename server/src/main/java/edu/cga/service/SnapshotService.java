package edu.cga.service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import edu.cga.common.TimeService;
import edu.cga.domain.entity.AuditLog;
import edu.cga.domain.entity.Budget;
import edu.cga.domain.entity.Contract;
import edu.cga.domain.entity.Department;
import edu.cga.domain.entity.Evaluation;
import edu.cga.domain.entity.Job;
import edu.cga.domain.entity.JobApplication;
import edu.cga.domain.entity.Notification;
import edu.cga.domain.entity.Payroll;
import edu.cga.domain.entity.SysConfig;
import edu.cga.domain.entity.Timesheet;
import edu.cga.domain.entity.User;
import edu.cga.domain.mapper.AuditLogMapper;
import edu.cga.domain.mapper.BudgetMapper;
import edu.cga.domain.mapper.ContractMapper;
import edu.cga.domain.mapper.DepartmentMapper;
import edu.cga.domain.mapper.EvaluationMapper;
import edu.cga.domain.mapper.JobApplicationMapper;
import edu.cga.domain.mapper.JobMapper;
import edu.cga.domain.mapper.NotificationMapper;
import edu.cga.domain.mapper.PayrollMapper;
import edu.cga.domain.mapper.SysConfigMapper;
import edu.cga.domain.mapper.TimesheetMapper;
import edu.cga.domain.mapper.UserMapper;
import edu.cga.rule.RuleEngine;
import edu.cga.scope.DataScope;
import edu.cga.scope.Masker;
import edu.cga.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 生成 GET /api/snapshot 的数据：结构与前端 mock 的 db 对象一致，
 * 每个集合只含当前用户可见的行（过滤条件由 {@link DataScope} 在 SQL 层生成）。
 */
@Service
public class SnapshotService {

    /** 与 mock 的 meta.version 一致 */
    public static final String DATA_VERSION = "1.1.0";

    private final DataScope scope;
    private final TimeService time;
    private final DepartmentMapper departmentMapper;
    private final UserMapper userMapper;
    private final BudgetMapper budgetMapper;
    private final SysConfigMapper sysConfigMapper;
    private final JobMapper jobMapper;
    private final JobApplicationMapper applicationMapper;
    private final ContractMapper contractMapper;
    private final TimesheetMapper timesheetMapper;
    private final EvaluationMapper evaluationMapper;
    private final PayrollMapper payrollMapper;
    private final NotificationMapper notificationMapper;
    private final AuditLogMapper auditLogMapper;
    private final JobTemplateService jobTemplateService;

    public SnapshotService(DataScope scope, TimeService time, DepartmentMapper departmentMapper,
                           UserMapper userMapper, BudgetMapper budgetMapper, SysConfigMapper sysConfigMapper,
                           JobMapper jobMapper, JobApplicationMapper applicationMapper, ContractMapper contractMapper,
                           TimesheetMapper timesheetMapper, EvaluationMapper evaluationMapper,
                           PayrollMapper payrollMapper, NotificationMapper notificationMapper,
                           AuditLogMapper auditLogMapper, JobTemplateService jobTemplateService) {
        this.scope = scope;
        this.time = time;
        this.departmentMapper = departmentMapper;
        this.userMapper = userMapper;
        this.budgetMapper = budgetMapper;
        this.sysConfigMapper = sysConfigMapper;
        this.jobMapper = jobMapper;
        this.applicationMapper = applicationMapper;
        this.contractMapper = contractMapper;
        this.timesheetMapper = timesheetMapper;
        this.evaluationMapper = evaluationMapper;
        this.payrollMapper = payrollMapper;
        this.notificationMapper = notificationMapper;
        this.auditLogMapper = auditLogMapper;
        this.jobTemplateService = jobTemplateService;
    }

    /** 只读事务：保证一次快照里各集合来自同一时刻 */
    @Transactional(readOnly = true)
    public Map<String, Object> snapshot(CurrentUser u) {
        Map<String, Object> db = new LinkedHashMap<>();
        db.put("meta", meta());
        db.put("departments", departmentMapper.selectList(new QueryWrapper<Department>().orderByAsc("id")));
        db.put("users", users(u));
        db.put("budgets", budgets(u));
        db.put("configs", configs());
        db.put("jobs", jobMapper.selectList(
                scope.jobs(u).applyTo(new QueryWrapper<Job>()).orderByDesc("created_at").orderByAsc("id")));
        db.put("applications", applicationMapper.selectList(
                scope.applications(u).applyTo(new QueryWrapper<JobApplication>())
                        .orderByDesc("applied_at").orderByAsc("id")));
        db.put("contracts", contractMapper.selectList(
                scope.contracts(u).applyTo(new QueryWrapper<Contract>()).orderByAsc("id")));
        db.put("timesheets", timesheetMapper.selectList(
                scope.timesheets(u).applyTo(new QueryWrapper<Timesheet>())
                        .orderByDesc("submitted_at").orderByAsc("id")));
        db.put("evaluations", evaluationMapper.selectList(
                scope.evaluations(u).applyTo(new QueryWrapper<Evaluation>())
                        .orderByDesc("evaluated_at").orderByAsc("id")));
        db.put("payrolls", payrollMapper.selectList(
                scope.payrolls(u).applyTo(new QueryWrapper<Payroll>()).orderByDesc("month").orderByAsc("id")));
        db.put("notifications", notificationMapper.selectList(
                scope.notifications(u).applyTo(new QueryWrapper<Notification>())
                        .orderByDesc("created_at").orderByAsc("id")));
        db.put("auditLogs", auditLogMapper.selectList(
                scope.auditLogs(u).applyTo(new QueryWrapper<AuditLog>()).orderByDesc("log_time").orderByAsc("id")));
        // P1：岗位模板（全校通用 + 本院系，见 DataScope.jobTemplates）
        db.put("jobTemplates", jobTemplateService.list(u));
        return db;
    }

    /** meta 不建表：版本号 + 由 TimeService 推算的当前周次、月份、日期 */
    public Map<String, Object> meta() {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("version", DATA_VERSION);
        meta.put("currentWeekNo", time.currentWeekNo());
        meta.put("currentMonth", time.currentMonth());
        meta.put("today", time.today().toString());
        meta.put("semesterStart", time.semesterStart().toString());
        return meta;
    }

    /** configs：{ workflows, evaluationTemplates, globalRules, jobTypeSchemas }，所有人可见 */
    public Map<String, Object> configs() {
        Map<String, Object> configs = new LinkedHashMap<>();
        for (String key : List.of(SysConfig.WORKFLOWS, SysConfig.EVALUATION_TEMPLATES, SysConfig.GLOBAL_RULES,
                SysConfig.JOB_TYPE_SCHEMAS)) {
            SysConfig row = sysConfigMapper.selectById(key);
            configs.put(key, row != null ? row.getConfigValue() : Map.of());
        }
        return configs;
    }

    /**
     * 用户：passwordHash 由实体上的 @JsonIgnore 保证永不输出；
     * 手机号/邮箱仅本人和校级管理员明文；学生看到的其他用户（导师）只保留姓名、职称、院系。
     */
    private List<Object> users(CurrentUser u) {
        List<User> rows = userMapper.selectList(
                scope.users(u).applyTo(new QueryWrapper<User>()).orderByAsc("id"));
        fillHoursSummary(rows);
        boolean plain = u.is(CurrentUser.SCHOOL_ADMIN);
        return rows.stream().map(row -> {
            if (row.getId().equals(u.id()) || plain) {
                return (Object) row;
            }
            if (u.is(CurrentUser.STUDENT)) {
                Map<String, Object> pub = new LinkedHashMap<>();
                pub.put("id", row.getId());
                pub.put("name", row.getName());
                pub.put("title", row.getTitle());
                pub.put("deptId", row.getDeptId());
                return pub;
            }
            row.setPhone(Masker.phone(row.getPhone()));
            row.setEmail(Masker.email(row.getEmail()));
            return row;
        }).toList();
    }

    /**
     * 给可见用户中的每个学生加 hoursSummary: { allocated, max }。
     * allocated 按该生全校全部生效和待签合同汇总（不受查看人数据范围限制），只给合计数，不给合同明细：
     * 导师能看到学生的跨岗总负荷，又看不到外院的合同细节。
     */
    @SuppressWarnings("unchecked")
    private void fillHoursSummary(List<User> rows) {
        // 只取计算所需的列；合同明细留在服务端，返回给前端的只有每个学生的一个峰值数字
        List<String> studentIds = rows.stream()
                .filter(r -> CurrentUser.STUDENT.equals(r.getRole())).map(User::getId).toList();
        Map<String, Integer> allocated = new HashMap<>();
        if (!studentIds.isEmpty()) {
            List<Contract> contracts = contractMapper.selectList(new QueryWrapper<Contract>()
                    .select("student_id", "weekly_hours", "start_date", "end_date", "status")
                    .in("status", RuleEngine.ALLOCATING_CONTRACT_STATUSES)
                    .in("student_id", studentIds));
            for (String id : studentIds) {
                allocated.put(id, RuleEngine.getStudentAllocatedWeeklyHours(contracts, id, time.today(), null));
            }
        }
        SysConfig rulesRow = sysConfigMapper.selectById(SysConfig.GLOBAL_RULES);
        Map<String, Object> rules = rulesRow != null && rulesRow.getConfigValue() instanceof Map<?, ?> m
                ? (Map<String, Object>) m : Map.of();
        for (User row : rows) {
            if (CurrentUser.STUDENT.equals(row.getRole())) {
                Map<String, Integer> summary = new LinkedHashMap<>();
                summary.put("allocated", allocated.getOrDefault(row.getId(), 0));
                summary.put("max", RuleEngine.getMaxWeeklyHours(rules, row));
                row.setHoursSummary(summary);
            }
        }
    }

    /** 经费：学生只能看到自己薪酬引用的经费名称/编号/来源，不含金额 */
    private List<Budget> budgets(CurrentUser u) {
        List<Budget> rows = budgetMapper.selectList(
                scope.budgets(u).applyTo(new QueryWrapper<Budget>()).orderByAsc("id"));
        if (u.is(CurrentUser.STUDENT)) {
            rows.forEach(b -> {
                b.setTotalAmount(null);
                b.setUsedAmount(null);
                b.setFrozenAmount(null);
            });
        }
        return rows;
    }
}
