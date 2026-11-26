package edu.cga.report;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import edu.cga.common.BizException;
import edu.cga.common.TimeService;
import edu.cga.domain.entity.Budget;
import edu.cga.domain.entity.Contract;
import edu.cga.domain.entity.Department;
import edu.cga.domain.entity.Job;
import edu.cga.domain.entity.Payroll;
import edu.cga.domain.entity.Timesheet;
import edu.cga.domain.entity.User;
import edu.cga.domain.mapper.BudgetMapper;
import edu.cga.domain.mapper.ContractMapper;
import edu.cga.domain.mapper.DepartmentMapper;
import edu.cga.domain.mapper.JobMapper;
import edu.cga.domain.mapper.PayrollMapper;
import edu.cga.domain.mapper.TimesheetMapper;
import edu.cga.domain.mapper.UserMapper;
import edu.cga.scope.DataScope;
import edu.cga.security.CurrentUser;
import edu.cga.service.AuditService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 报表导出（P1 第二批 2.1）：GET /api/reports/{type}/export。
 * <p>
 * 数据范围与快照一致：每张主表都用 {@link DataScope} 的同一个条件在 SQL 层过滤，再叠加 month / deptId 筛选；
 * 学生姓名、岗位名称等只按主表查出的 ID 回查，不查全表后在内存里过滤。每次导出写一条审计日志。
 */
@Service
public class ReportExportService {

    /** 报表类型：路径参数 → 中文名（也用作文件名和工作表名） */
    public enum ReportType {
        JOBS("jobs", "岗位统计"),
        HIRES("hires", "聘用名单"),
        TIMESHEETS("timesheets", "工时汇总"),
        PAYROLLS("payrolls", "薪酬发放"),
        BUDGETS("budgets", "经费执行");

        public final String code;
        public final String title;

        ReportType(String code, String title) {
            this.code = code;
            this.title = title;
        }

        public static ReportType of(String code) {
            for (ReportType t : values()) {
                if (t.code.equals(code)) {
                    return t;
                }
            }
            throw BizException.badRequest("不支持的报表类型：" + code);
        }
    }

    /** 导出结果 */
    public record ExportFile(String fileName, String contentType, byte[] content, int rowCount) {
    }

    public static final String XLSX_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    public static final String CSV_TYPE = "text/csv;charset=UTF-8";

    private static final Pattern MONTH = Pattern.compile("\\d{4}-(0[1-9]|1[0-2])");

    private static final Map<String, String> TYPE_LABELS = Map.of("TA", "助教（TA）", "RA", "助研（RA）", "GA", "助管（GA）");
    private static final Map<String, String> JOB_STATUS = Map.of(
            "DRAFT", "草稿", "PENDING_DEPT_REVIEW", "待院系初审", "PENDING_INTL_REVIEW", "待国际生办会签",
            "PENDING_SCHOOL_REVIEW", "待校级终审", "PUBLISHED", "已正式发布", "REJECTED", "已驳回", "CLOSED", "已招满关闭");
    private static final Map<String, String> CONTRACT_STATUS = Map.of(
            "PENDING_SIGN", "待签署", "ACTIVE", "在岗履约中", "TERMINATED", "已解除", "EXPIRED", "已到期");
    private static final Map<String, String> FINANCE_STATUS = Map.of(
            "PENDING_CALC", "待核算", "PENDING_PAY", "待财务打款", "SUBMITTED", "已提交财务",
            "PAY_FAILED", "发放失败", "PAID", "已发放");

    private final DataScope scope;
    private final TimeService time;
    private final AuditService audit;
    private final JobMapper jobMapper;
    private final ContractMapper contractMapper;
    private final TimesheetMapper timesheetMapper;
    private final PayrollMapper payrollMapper;
    private final BudgetMapper budgetMapper;
    private final UserMapper userMapper;
    private final DepartmentMapper departmentMapper;

    public ReportExportService(DataScope scope, TimeService time, AuditService audit, JobMapper jobMapper,
                               ContractMapper contractMapper, TimesheetMapper timesheetMapper,
                               PayrollMapper payrollMapper, BudgetMapper budgetMapper, UserMapper userMapper,
                               DepartmentMapper departmentMapper) {
        this.scope = scope;
        this.time = time;
        this.audit = audit;
        this.jobMapper = jobMapper;
        this.contractMapper = contractMapper;
        this.timesheetMapper = timesheetMapper;
        this.payrollMapper = payrollMapper;
        this.budgetMapper = budgetMapper;
        this.userMapper = userMapper;
        this.departmentMapper = departmentMapper;
    }

    /**
     * 生成报表文件并写审计日志。
     *
     * @param month  yyyy-MM，可空：工时、薪酬按该月筛选，聘用名单筛选聘期覆盖该月的合同；岗位、经费不按月筛选
     * @param deptId 可空：只导出该院系（仍受数据范围约束，越权的院系得到空表）
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ExportFile export(String typeCode, String format, String month, String deptId, CurrentUser user) {
        ReportType type = ReportType.of(typeCode);
        String fmt = format == null || format.isBlank() ? "xlsx" : format.trim().toLowerCase();
        if (!fmt.equals("xlsx") && !fmt.equals("csv")) {
            throw BizException.badRequest("导出格式只支持 xlsx 或 csv");
        }
        String m = blankToNull(month);
        if (m != null && !MONTH.matcher(m).matches()) {
            throw BizException.badRequest("月份格式应为 yyyy-MM");
        }
        String dept = blankToNull(deptId);

        ReportTable table = switch (type) {
            case JOBS -> jobs(user, dept);
            case HIRES -> hires(user, m, dept);
            case TIMESHEETS -> timesheets(user, m, dept);
            case PAYROLLS -> payrolls(user, m, dept);
            case BUDGETS -> budgets(user, dept);
        };

        byte[] content = fmt.equals("xlsx") ? ReportWriter.xlsx(table) : ReportWriter.csv(table);
        String fileName = type.title + "_" + (m != null ? m : time.currentMonth()) + "." + fmt;
        int rows = table.rows().size();

        audit.log("REPORT", type.code, user, "导出报表",
                "导出《" + type.title + "》" + fmt.toUpperCase() + "，共 " + rows + " 行"
                        + (m != null ? "，月份 " + m : "") + (dept != null ? "，院系 " + dept : ""),
                "", fileName);
        return new ExportFile(fileName, fmt.equals("xlsx") ? XLSX_TYPE : CSV_TYPE, content, rows);
    }

    // ------------------------------------------------------------------ 各报表

    /** 岗位统计：类型、院系、名额、已聘、预计成本（quota × 时薪 × 周工时 × 周数）、状态 */
    private ReportTable jobs(CurrentUser u, String dept) {
        QueryWrapper<Job> w = scope.jobs(u).applyTo(new QueryWrapper<Job>()
                .select("id", "type", "title", "dept_id", "mentor_id", "quota", "hired_count", "hourly_rate",
                        "weekly_hours", "weeks", "status"));
        if (dept != null) {
            w.eq("dept_id", dept);
        }
        List<Job> rows = jobMapper.selectList(w.orderByAsc("dept_id").orderByAsc("type").orderByAsc("id"));
        Map<String, String> depts = deptNames();
        Map<String, User> mentors = byId(userMapper, rows.stream().map(Job::getMentorId).toList(), User::getId);

        ReportTable t = new ReportTable(ReportType.JOBS.title,
                "岗位编号", "岗位名称", "类型", "院系", "导师", "名额", "已聘", "预计成本（元）", "状态");
        for (Job j : rows) {
            BigDecimal cost = nz(j.getHourlyRate())
                    .multiply(BigDecimal.valueOf((long) ni(j.getQuota()) * ni(j.getWeeklyHours()) * ni(j.getWeeks())))
                    .setScale(2, RoundingMode.HALF_UP);
            t.row(j.getId(), j.getTitle(), TYPE_LABELS.getOrDefault(j.getType(), j.getType()),
                    depts.getOrDefault(j.getDeptId(), j.getDeptId()), nameOf(mentors, j.getMentorId()),
                    ni(j.getQuota()), ni(j.getHiredCount()), cost, JOB_STATUS.getOrDefault(j.getStatus(), j.getStatus()));
        }
        return t;
    }

    /** 聘用名单：学生、学号、岗位、聘期、周工时、国际生、合同状态 */
    private ReportTable hires(CurrentUser u, String month, String dept) {
        QueryWrapper<Contract> w = scope.contracts(u).applyTo(new QueryWrapper<Contract>()
                .select("id", "student_id", "job_id", "dept_id", "start_date", "end_date", "weekly_hours", "status"));
        if (dept != null) {
            w.eq("dept_id", dept);
        }
        if (month != null) {
            // 聘期与该月有交集：开始日 ≤ 月末 且 结束日 ≥ 月初
            YearMonth ym = YearMonth.parse(month);
            w.le("start_date", ym.atEndOfMonth()).ge("end_date", ym.atDay(1));
        }
        List<Contract> rows = contractMapper.selectList(w.orderByAsc("start_date").orderByAsc("id"));
        Map<String, User> students = byId(userMapper, rows.stream().map(Contract::getStudentId).toList(), User::getId);
        Map<String, Job> jobs = byId(jobMapper, rows.stream().map(Contract::getJobId).toList(), Job::getId);
        Map<String, String> depts = deptNames();

        ReportTable t = new ReportTable(ReportType.HIRES.title,
                "协议编号", "学生", "学号", "岗位", "院系", "聘期", "周工时", "国际生", "合同状态");
        for (Contract c : rows) {
            User s = students.get(c.getStudentId());
            Job j = jobs.get(c.getJobId());
            t.row(c.getId(), s != null ? s.getName() : c.getStudentId(), s != null ? s.getStudentNo() : null,
                    j != null ? j.getTitle() : c.getJobId(), depts.getOrDefault(c.getDeptId(), c.getDeptId()),
                    c.getStartDate() + " ~ " + c.getEndDate(), ni(c.getWeeklyHours()),
                    s != null && Boolean.TRUE.equals(s.getIsInternational()) ? "是" : "否",
                    CONTRACT_STATUS.getOrDefault(c.getStatus(), c.getStatus()));
        }
        return t;
    }

    /** 工时汇总：学生 × 月份，已审核 / 待审核 / 驳回工时（SQL 分组汇总） */
    private ReportTable timesheets(CurrentUser u, String month, String dept) {
        QueryWrapper<Timesheet> w = scope.timesheets(u).applyTo(new QueryWrapper<Timesheet>().select(
                "student_id AS studentId", "month AS month",
                "SUM(CASE WHEN status = 'APPROVED' THEN hours ELSE 0 END) AS approved",
                "SUM(CASE WHEN status = 'PENDING' THEN hours ELSE 0 END) AS pending",
                "SUM(CASE WHEN status = 'REJECTED' THEN hours ELSE 0 END) AS rejected"));
        if (month != null) {
            w.eq("month", month);
        }
        if (dept != null) {
            w.eq("dept_id", dept);
        }
        w.groupBy("student_id", "month").orderByDesc("month").orderByAsc("student_id");
        List<Map<String, Object>> rows = timesheetMapper.selectMaps(w);
        Map<String, User> students = byId(userMapper,
                rows.stream().map(r -> String.valueOf(r.get("studentId"))).toList(), User::getId);

        ReportTable t = new ReportTable(ReportType.TIMESHEETS.title,
                "学生", "学号", "月份", "已审核工时", "待审核工时", "驳回工时");
        for (Map<String, Object> r : rows) {
            User s = students.get(String.valueOf(r.get("studentId")));
            t.row(s != null ? s.getName() : r.get("studentId"), s != null ? s.getStudentNo() : null, r.get("month"),
                    decimal(r.get("approved")), decimal(r.get("pending")), decimal(r.get("rejected")));
        }
        return t;
    }

    /** 薪酬发放：月份、学生、岗位、经费来源、金额、状态、凭证号 */
    private ReportTable payrolls(CurrentUser u, String month, String dept) {
        QueryWrapper<Payroll> w = scope.payrolls(u).applyTo(new QueryWrapper<Payroll>());
        if (month != null) {
            w.eq("month", month);
        }
        if (dept != null) {
            w.eq("dept_id", dept);
        }
        List<Payroll> rows = payrollMapper.selectList(w.orderByDesc("month").orderByAsc("id"));
        Map<String, User> students = byId(userMapper, rows.stream().map(Payroll::getStudentId).toList(), User::getId);
        Map<String, Job> jobs = byId(jobMapper, rows.stream().map(Payroll::getJobId).toList(), Job::getId);
        Map<String, Budget> budgets = byId(budgetMapper, rows.stream().map(Payroll::getBudgetId).toList(), Budget::getId);

        ReportTable t = new ReportTable(ReportType.PAYROLLS.title,
                "月份", "学生", "学号", "岗位", "经费来源", "经费编号", "工时", "绩效系数", "金额（元）", "状态", "凭证号");
        for (Payroll p : rows) {
            User s = students.get(p.getStudentId());
            Job j = jobs.get(p.getJobId());
            Budget b = budgets.get(p.getBudgetId());
            t.row(p.getMonth(), s != null ? s.getName() : p.getStudentId(), s != null ? s.getStudentNo() : null,
                    j != null ? j.getTitle() : p.getJobId(),
                    b != null ? b.getSourceLabel() + " · " + b.getName() : p.getSourceType(),
                    b != null ? b.getCode() : p.getBudgetId(), p.getTotalHours(), p.getCoefficient(), p.getAmount(),
                    FINANCE_STATUS.getOrDefault(p.getFinanceStatus(), p.getFinanceStatus()), p.getVoucherNo());
        }
        return t;
    }

    /** 经费执行：总额、已发、冻结、可用（总额 − 已发 − 冻结）、执行率（已发 / 总额） */
    private ReportTable budgets(CurrentUser u, String dept) {
        QueryWrapper<Budget> w = scope.budgets(u).applyTo(new QueryWrapper<Budget>());
        if (dept != null) {
            w.eq("dept_id", dept);
        }
        List<Budget> rows = budgetMapper.selectList(w.orderByAsc("dept_id").orderByAsc("id"));
        Map<String, String> depts = deptNames();

        ReportTable t = new ReportTable(ReportType.BUDGETS.title,
                "经费编号", "经费名称", "来源", "院系", "总额（元）", "已发（元）", "冻结（元）", "可用（元）", "执行率（%）");
        for (Budget b : rows) {
            BigDecimal total = nz(b.getTotalAmount());
            BigDecimal used = nz(b.getUsedAmount());
            BigDecimal frozen = nz(b.getFrozenAmount());
            BigDecimal rate = total.signum() > 0
                    ? used.multiply(BigDecimal.valueOf(100)).divide(total, 1, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO.setScale(1);
            t.row(b.getCode(), b.getName(), b.getSourceLabel(), depts.getOrDefault(b.getDeptId(), b.getDeptId()),
                    total, used, frozen, total.subtract(used).subtract(frozen), rate);
        }
        return t;
    }

    // ------------------------------------------------------------------ 工具

    /** 按主表查出的 ID 回查关联记录（只查这些 ID，不查全表） */
    private static <T> Map<String, T> byId(BaseMapper<T> mapper, Collection<String> ids, Function<T, String> idOf) {
        Set<String> distinct = ids.stream().filter(id -> id != null && !id.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (distinct.isEmpty()) {
            return Map.of();
        }
        Map<String, T> map = new HashMap<>();
        for (T row : mapper.selectList(new QueryWrapper<T>().in("id", distinct))) {
            map.put(idOf.apply(row), row);
        }
        return map;
    }

    /** 院系名称（院系表所有人可见） */
    private Map<String, String> deptNames() {
        Map<String, String> map = new HashMap<>();
        for (Department d : departmentMapper.selectList(new QueryWrapper<Department>().select("id", "name"))) {
            map.put(d.getId(), d.getName());
        }
        return map;
    }

    private static String nameOf(Map<String, User> users, String id) {
        User u = users.get(id);
        return u != null ? u.getName() : id;
    }

    private static BigDecimal decimal(Object v) {
        if (v instanceof BigDecimal bd) {
            return bd.setScale(1, RoundingMode.HALF_UP);
        }
        if (v instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue()).setScale(1, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO.setScale(1);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static int ni(Integer v) {
        return v == null ? 0 : v;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
