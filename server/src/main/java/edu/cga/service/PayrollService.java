package edu.cga.service;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.annotation.JsonIgnore;
import edu.cga.common.BizException;
import edu.cga.common.IdGenerator;
import edu.cga.common.Num;
import edu.cga.common.TimeService;
import edu.cga.domain.entity.Budget;
import edu.cga.domain.entity.Contract;
import edu.cga.domain.entity.Job;
import edu.cga.domain.entity.Payroll;
import edu.cga.domain.entity.Timesheet;
import edu.cga.domain.mapper.BudgetMapper;
import edu.cga.domain.mapper.ContractMapper;
import edu.cga.domain.mapper.EvaluationMapper;
import edu.cga.domain.mapper.JobMapper;
import edu.cga.domain.mapper.PayrollMapper;
import edu.cga.domain.mapper.TimesheetMapper;
import edu.cga.rule.RuleEngine;
import edu.cga.scope.DataScope;
import edu.cga.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 薪酬：月度核算（generateMonthlyPayrolls）与财务发放（confirmPayrollPayout）。
 * <p>
 * 薪酬与预算联动（设计文档第 7 节）：
 * <ul>
 *   <li>岗位剩余预留 = reservedAmount − 该岗位下已发放和待发放薪酬之和（不低于 0）；</li>
 *   <li>核算：金额 ≤ 剩余预留 + 经费可用额，否则不生成，列入 blocked；</li>
 *   <li>发放：usedAmount += 金额；frozenAmount −= min(金额, 发放前该岗位剩余预留)。</li>
 * </ul>
 * 加锁顺序固定为“经费行 → 薪酬行”，核算、发放、考核改系数三处一致，避免死锁。
 */
@Service
public class PayrollService {

    private static final Pattern MONTH = Pattern.compile("\\d{4}-(0[1-9]|1[0-2])");

    private final PayrollMapper payrollMapper;
    private final ContractMapper contractMapper;
    private final TimesheetMapper timesheetMapper;
    private final EvaluationMapper evaluationMapper;
    private final JobMapper jobMapper;
    private final BudgetMapper budgetMapper;
    private final DataScope scope;
    private final AuditService audit;
    private final IdGenerator ids;
    private final TimeService time;
    private final SecureRandom random = new SecureRandom();

    public PayrollService(PayrollMapper payrollMapper, ContractMapper contractMapper, TimesheetMapper timesheetMapper,
                          EvaluationMapper evaluationMapper, JobMapper jobMapper, BudgetMapper budgetMapper,
                          DataScope scope, AuditService audit, IdGenerator ids, TimeService time) {
        this.payrollMapper = payrollMapper;
        this.contractMapper = contractMapper;
        this.timesheetMapper = timesheetMapper;
        this.evaluationMapper = evaluationMapper;
        this.jobMapper = jobMapper;
        this.budgetMapper = budgetMapper;
        this.scope = scope;
        this.audit = audit;
        this.ids = ids;
        this.time = time;
    }

    /** 被预算拦截的一笔核算 */
    public record BlockedPayroll(String contractId, String studentId, BigDecimal amount, String reason) {
    }

    /** 核算结果 { count, blocked }；month 只用于拼接提示文案，不输出到 JSON */
    public record GenerateResult(int count, List<BlockedPayroll> blocked, @JsonIgnore String month) {
    }

    // ------------------------------------------------------------------ generateMonthlyPayrolls

    /** 院系管理员只核算本院系，校级管理员核算全校 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public GenerateResult generate(String month, CurrentUser operator) {
        String targetMonth = month == null || month.isBlank() ? time.currentMonth() : month.trim();
        if (!MONTH.matcher(targetMonth).matches()) {
            throw BizException.badRequest("核算月份格式应为 yyyy-MM");
        }
        // 核算范围 = 操作人数据范围内的生效合同（院系管理员即本院系，校级管理员即全校）
        List<Contract> contracts = contractMapper.selectList(scope.contracts(operator)
                .applyTo(new QueryWrapper<Contract>()).eq("status", "ACTIVE").orderByAsc("id"));

        // 先收集本次涉及的岗位，按经费 ID 排序后逐个加锁（固定顺序，避免与其他核算/发放互相等待成环）
        Map<String, Job> jobs = new LinkedHashMap<>();
        for (Contract con : contracts) {
            Job job = jobMapper.selectById(con.getJobId());
            if (job != null) {
                jobs.put(job.getId(), job);
            }
        }
        Map<String, Budget> budgets = new TreeMap<>();
        jobs.values().forEach(j -> budgets.put(j.getBudgetId(), null));
        budgets.replaceAll((id, ignored) -> budgetMapper.lockById(id));

        int count = 0;
        List<BlockedPayroll> blocked = new ArrayList<>();
        for (Contract con : contracts) {
            List<Timesheet> approved = timesheetMapper.selectApprovedByContractAndMonth(con.getId(), targetMonth);
            Job job = jobs.get(con.getJobId());
            if (approved.isEmpty() || job == null) {
                continue;
            }
            BigDecimal totalHours = approved.stream().map(t -> Num.orZero(t.getHours()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            Budget budget = budgets.get(job.getBudgetId());

            // 最近一次考核的系数，没有考核按 1.0
            BigDecimal latestCoeff = evaluationMapper.selectLatestCoefficient(con.getId());
            BigDecimal coeff = latestCoeff != null ? latestCoeff : BigDecimal.ONE;
            BigDecimal amount = RuleEngine.calcPayrollAmount(totalHours, con.getHourlyRate(), coeff);

            Payroll existing = payrollMapper.selectByContractAndMonth(con.getId(), targetMonth);
            if (existing != null && "PAID".equals(existing.getFinanceStatus())) {
                continue;
            }

            BigDecimal committedOthers = payrollMapper.sumCommittedByJob(job.getId(),
                    existing != null ? existing.getId() : null);
            BigDecimal excessOthers = payrollMapper.sumUnpaidExcessByBudget(job.getBudgetId(),
                    existing != null ? existing.getId() : null);
            RuleEngine.PayrollBudgetCheck check = RuleEngine.checkPayrollBudget(amount, job.getReservedAmount(),
                    committedOthers, budget, excessOthers);
            if (!check.ok()) {
                blocked.add(new BlockedPayroll(con.getId(), con.getStudentId(), amount, check.reason()));
                continue;
            }

            if (existing != null) {
                existing.setTotalHours(totalHours);
                existing.setCoefficient(coeff);
                existing.setAmount(amount);
                existing.setFinanceStatus("PENDING_PAY");
                existing.setCalculatedAt(time.now());
                payrollMapper.updateById(existing);
            } else {
                Payroll p = new Payroll();
                p.setId(ids.next("PAY"));
                p.setStudentId(con.getStudentId());
                p.setContractId(con.getId());
                p.setJobId(con.getJobId());
                p.setDeptId(con.getDeptId());
                p.setMonth(targetMonth);
                p.setTotalHours(totalHours);
                p.setHourlyRate(con.getHourlyRate());
                p.setCoefficient(coeff);
                p.setAmount(amount);
                p.setBudgetId(job.getBudgetId());
                p.setSourceType(budget != null && budget.getSourceType() != null ? budget.getSourceType() : "TA_FUND");
                p.setFinanceStatus("PENDING_PAY");
                p.setCalculatedAt(time.now());
                payrollMapper.insert(p);
            }
            count++;
        }

        StringBuilder opinion = new StringBuilder("汇总 " + targetMonth + " 已审核通过工时并应用考核绩效系数，共核算 " + count
                + " 笔单据转入待财务打款。");
        if (!blocked.isEmpty()) {
            opinion.append("另有 ").append(blocked.size()).append(" 笔超出岗位剩余预留与经费可用额，已拦截未生成：");
            for (int i = 0; i < blocked.size(); i++) {
                BlockedPayroll b = blocked.get(i);
                opinion.append(i > 0 ? "；" : "").append(b.contractId()).append("（¥").append(Num.locale(b.amount()))
                        .append("）");
            }
            opinion.append("。");
        }
        audit.log("PAYROLL", "BATCH_" + targetMonth, operator, "执行月度薪酬自动核算", opinion.toString(), "待核算",
                "待财务打款（" + count + " 笔）" + (blocked.isEmpty() ? "" : "，预算拦截 " + blocked.size() + " 笔"));
        return new GenerateResult(count, blocked, targetMonth);
    }

    // ------------------------------------------------------------------ confirmPayrollPayout

    /**
     * 校级管理员确认发放：锁经费行 → 锁薪酬行，校验后 usedAmount 增加、frozenAmount 只扣本岗位的预留部分。
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Payroll payout(String payrollId, CurrentUser operator) {
        Payroll visible = scope.requireVisible(payrollMapper, scope.payrolls(operator), payrollId, "薪酬单不存在");
        Budget budget = budgetMapper.lockById(visible.getBudgetId());
        if (budget == null) {
            throw BizException.notFound("关联经费池不存在");
        }
        Payroll pay = payrollMapper.lockById(payrollId);
        if ("PAID".equals(pay.getFinanceStatus())) {
            throw BizException.badRequest("该笔薪酬已发放，请勿重复打款");
        }
        if (!"PENDING_PAY".equals(pay.getFinanceStatus())) {
            throw BizException.badRequest("该笔薪酬尚未核算，请先执行月度核算");
        }

        BigDecimal amount = Num.orZero(pay.getAmount());
        BigDecimal used = Num.orZero(budget.getUsedAmount());
        BigDecimal frozen = Num.orZero(budget.getFrozenAmount());
        // 规格要求：发放时再校验一次 amount ≤ total − used
        if (amount.compareTo(Num.orZero(budget.getTotalAmount()).subtract(used)) > 0) {
            throw BizException.badRequest("经费池「" + budget.getName() + "」剩余总额不足，拦截打款");
        }
        Job job = jobMapper.selectById(pay.getJobId());
        BigDecimal reserved = job != null ? job.getReservedAmount() : BigDecimal.ZERO;
        BigDecimal paidBefore = payrollMapper.sumPaidByJob(pay.getJobId());
        BigDecimal release = RuleEngine.payoutFrozenRelease(amount, reserved, paidBefore);
        // 超出本岗位预留的部分只能动用经费可用额，不能挪用其他岗位的预留
        // 同经费池其他待发放薪酬已承诺的超额也要让出来，否则先发的会挤占后发的
        BigDecimal excessOthers = payrollMapper.sumUnpaidExcessByBudget(budget.getId(), pay.getId());
        if (amount.subtract(release).compareTo(RuleEngine.getBudgetAvailable(budget).subtract(excessOthers)) > 0) {
            throw BizException.badRequest("经费池「" + budget.getName() + "」可用额不足（超出岗位预留部分无可用经费），拦截打款");
        }

        budget.setUsedAmount(Num.money(used.add(amount)));
        budget.setFrozenAmount(Num.money(frozen.subtract(release).max(BigDecimal.ZERO)));
        budgetMapper.updateById(budget);

        String voucherNo = "CW-" + pay.getMonth().replace("-", "") + "-" + (1000 + random.nextInt(9000));
        pay.setFinanceStatus("PAID");
        pay.setPaidAt(time.now());
        pay.setVoucherNo(voucherNo);
        payrollMapper.updateById(pay);

        audit.notify(pay.getStudentId(), AuditService.CHANNEL_MAIL,
                pay.getMonth() + " 三助薪酬已发放：¥" + Num.fixed2(amount),
                "财务打款凭证号 " + voucherNo + "，审核工时 " + Num.plain(pay.getTotalHours()) + "h × ¥"
                        + Num.plain(pay.getHourlyRate()) + "/h × 考核系数 " + Num.plain(pay.getCoefficient()) + "x。");
        audit.log("PAYROLL", pay.getId(), operator, "确认财务发放并联动扣减经费池",
                "发放金额 ¥" + Num.fixed2(amount) + "，凭证号 " + voucherNo + "，经费池「" + budget.getName() + "」实时扣减。",
                "待打款（经费已用 ¥" + Num.locale(used) + "）",
                "已发放（经费已用 ¥" + Num.locale(budget.getUsedAmount()) + "）");
        return pay;
    }
}
