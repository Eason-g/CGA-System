package edu.cga.service;

import java.time.format.DateTimeFormatter;

import edu.cga.common.BizException;
import edu.cga.common.IdGenerator;
import edu.cga.common.Num;
import edu.cga.common.TimeService;
import edu.cga.config.MaterialFinanceProperties;
import edu.cga.domain.entity.Budget;
import edu.cga.domain.entity.FinanceTransaction;
import edu.cga.domain.entity.Payroll;
import edu.cga.domain.mapper.BudgetMapper;
import edu.cga.domain.mapper.FinanceTransactionMapper;
import edu.cga.domain.mapper.PayrollMapper;
import edu.cga.finance.FinanceGateway;
import edu.cga.finance.FinanceSignature;
import edu.cga.scope.DataScope;
import edu.cga.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 财务对接与状态回写（P1 第三批 3.3）。
 * <ol>
 *   <li>提交财务：PENDING_PAY / PAY_FAILED → SUBMITTED，生成流水号，经费不变；提交前按 P0 发放规则预检一次；</li>
 *   <li>财务回调：校验 HMAC-SHA256 签名（错误 401），锁流水行保证幂等；成功 → PAID，按 P0 规则扣经费
 *       （与一步发放共用 {@link PayrollService#settlePayout}）；失败 → PAY_FAILED，写原因，经费不变，可重新提交。</li>
 * </ol>
 * 加锁顺序：流水行 → 经费行 → 薪酬行（经费行 → 薪酬行与核算、一步发放一致）。
 */
@Service
public class FinanceService {

    /** 财务回调的操作人（审计日志里显示为“学校财务系统”） */
    public static final CurrentUser FINANCE_SYSTEM = new CurrentUser(null, "finance-system", "学校财务系统",
            "SYSTEM", "财务系统接口", null, false);

    private static final DateTimeFormatter SERIAL_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 回调处理结果；duplicate=true 表示该流水号此前已处理，本次未做任何变更 */
    public record CallbackResult(String serialNo, String payrollId, String transactionStatus, String financeStatus,
                                 boolean duplicate) {
    }

    private final PayrollMapper payrollMapper;
    private final BudgetMapper budgetMapper;
    private final FinanceTransactionMapper txnMapper;
    private final PayrollService payrollService;
    private final FinanceGateway gateway;
    private final DataScope scope;
    private final AuditService audit;
    private final IdGenerator ids;
    private final TimeService time;
    private final JsonMapper json;
    private final MaterialFinanceProperties props;
    private final TransactionTemplate txTemplate;

    public FinanceService(PayrollMapper payrollMapper, BudgetMapper budgetMapper, FinanceTransactionMapper txnMapper,
                          PayrollService payrollService, FinanceGateway gateway, DataScope scope, AuditService audit,
                          IdGenerator ids, TimeService time, JsonMapper json, MaterialFinanceProperties props,
                          PlatformTransactionManager txManager) {
        this.payrollMapper = payrollMapper;
        this.budgetMapper = budgetMapper;
        this.txnMapper = txnMapper;
        this.payrollService = payrollService;
        this.gateway = gateway;
        this.scope = scope;
        this.audit = audit;
        this.ids = ids;
        this.time = time;
        this.json = json;
        this.props = props;
        this.txTemplate = new TransactionTemplate(txManager);
        this.txTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    public FinanceGateway gateway() {
        return gateway;
    }

    // ------------------------------------------------------------------ 提交财务

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Payroll submit(String payrollId, CurrentUser operator) {
        Payroll visible = scope.requireVisible(payrollMapper, scope.payrolls(operator), payrollId, "薪酬单不存在");
        Budget budget = budgetMapper.lockById(visible.getBudgetId());
        if (budget == null) {
            throw BizException.notFound("关联经费池不存在");
        }
        Payroll pay = payrollMapper.lockById(payrollId);
        String status = pay.getFinanceStatus();
        if ("PAID".equals(status)) {
            throw BizException.badRequest("该笔薪酬已发放，请勿重复提交");
        }
        if ("SUBMITTED".equals(status)) {
            throw BizException.badRequest("该笔薪酬已提交财务（流水号 " + pay.getFinanceSerialNo() + "），请等待财务系统回写结果");
        }
        if (!"PENDING_PAY".equals(status) && !"PAY_FAILED".equals(status)) {
            throw BizException.badRequest("该笔薪酬尚未核算，请先执行月度核算");
        }
        // 预检：按 P0 发放规则此刻就发不出去的，不提交给财务（回调时还会再按同一规则校验一次）
        payrollService.checkPayout(budget, pay);

        boolean resubmit = "PAY_FAILED".equals(status);
        String serialNo = ids.next("FIN" + time.today().format(SERIAL_DATE));
        FinanceTransaction txn = new FinanceTransaction();
        txn.setSerialNo(serialNo);
        txn.setPayrollId(pay.getId());
        txn.setAmount(Num.orZero(pay.getAmount()));
        txn.setStatus(FinanceTransaction.SUBMITTED);
        txn.setSubmittedBy(operator.id());
        txn.setSubmittedAt(time.now());
        txnMapper.insert(txn);

        pay.setFinanceStatus("SUBMITTED");
        pay.setFinanceSerialNo(serialNo);
        pay.setSubmittedAt(txn.getSubmittedAt());
        pay.setFailReason("");
        payrollMapper.updateById(pay);

        gateway.submit(new FinanceGateway.PaymentOrder(serialNo, pay.getId(), pay.getStudentId(), pay.getMonth(),
                txn.getAmount()));
        audit.log("PAYROLL", pay.getId(), operator, resubmit ? "重新提交财务发放" : "提交财务发放",
                "金额 ¥" + Num.fixed2(txn.getAmount()) + "，财务流水号 " + serialNo + "；经费在财务回写成功后扣减。",
                resubmit ? "发放失败" : "待打款", "已提交财务");
        return pay;
    }

    // ------------------------------------------------------------------ 财务回调

    /**
     * 处理财务回调：先校验签名（失败 401），再在一个事务里按流水号幂等地回写。
     * 公开接口与模拟网关都走这里。
     */
    public CallbackResult handleCallback(String rawBody, String signature) {
        if (!FinanceSignature.verify(props.getFinance().getCallbackSecret(), rawBody, signature)) {
            throw new BizException(401, "回调签名校验失败");
        }
        JsonNode body;
        try {
            body = json.readTree(rawBody);
        } catch (JacksonException e) {
            throw BizException.badRequest("回调请求体不是合法 JSON");
        }
        String serialNo = text(body, "serialNo");
        if (serialNo == null) {
            throw BizException.badRequest("回调缺少流水号 serialNo");
        }
        JsonNode successNode = body.path("success");
        if (!successNode.isBoolean()) {
            throw BizException.badRequest("回调缺少处理结果 success");
        }
        boolean success = successNode.asBoolean();
        String voucherNo = text(body, "voucherNo");
        String reason = text(body, "reason");
        if (voucherNo != null && voucherNo.length() > 32) {
            throw BizException.badRequest("凭证号过长");
        }
        return txTemplate.execute(s -> applyCallback(serialNo, success, voucherNo, reason));
    }

    private CallbackResult applyCallback(String serialNo, boolean success, String voucherNo, String reason) {
        FinanceTransaction txn = txnMapper.lockBySerialNo(serialNo);
        if (txn == null) {
            throw BizException.notFound("财务流水号不存在");
        }
        boolean reconciling = FinanceTransaction.RECONCILE.equals(txn.getStatus());
        if ((!FinanceTransaction.SUBMITTED.equals(txn.getStatus()) && !reconciling) || (reconciling && !success)) {
            // 幂等：同一流水号已处理过，直接返回当时的结果，不重复扣款
            Payroll p = payrollMapper.selectById(txn.getPayrollId());
            return new CallbackResult(serialNo, txn.getPayrollId(), txn.getStatus(),
                    p != null ? p.getFinanceStatus() : null, true);
        }
        Payroll visible = payrollMapper.selectById(txn.getPayrollId());
        if (visible == null) {
            throw BizException.notFound("关联薪酬单不存在");
        }
        Budget budget = budgetMapper.lockById(visible.getBudgetId());
        Payroll pay = payrollMapper.lockById(visible.getId());

        String failReason = null;
        if (!"SUBMITTED".equals(pay.getFinanceStatus()) || !serialNo.equals(pay.getFinanceSerialNo())) {
            failReason = "流水已失效（薪酬单当前不在该流水的已提交状态）";
        } else if (success) {
            String voucher = reconciling ? txn.getVoucherNo() : voucherNo;
            if (voucher == null) {
                voucher = "CW-" + pay.getMonth().replace("-", "") + "-" + serialNo.substring(serialNo.length() - 4);
            }
            try {
                if (budget == null) {
                    throw BizException.notFound("关联经费池不存在");
                }
                payrollService.settlePayout(budget, pay, voucher, FINANCE_SYSTEM, "财务回写发放成功并联动扣减经费池");
                txn.setStatus(FinanceTransaction.SUCCESS);
                txn.setVoucherNo(voucher);
                txn.setFailReason("");
                txn.setCallbackAt(time.now());
                txnMapper.updateById(txn);
                return new CallbackResult(serialNo, pay.getId(), txn.getStatus(), pay.getFinanceStatus(), false);
            } catch (BizException e) {
                if (!reconciling) {
                    String reasonText = "财务已确认成功，内部记账待核对：" + e.getMessage();
                    txn.setStatus(FinanceTransaction.RECONCILE);
                    txn.setVoucherNo(voucher);
                    txn.setCallbackAt(time.now());
                    txn.setFailReason(reasonText.substring(0, Math.min(255, reasonText.length())));
                    txnMapper.updateById(txn);
                    pay.setFailReason(txn.getFailReason());
                    payrollMapper.updateById(pay);
                    audit.log("PAYROLL", pay.getId(), FINANCE_SYSTEM, "财务回执待对账",
                            reasonText, "已提交财务", "财务回执待对账");
                }
                return new CallbackResult(serialNo, pay.getId(), txn.getStatus(), pay.getFinanceStatus(), reconciling);
            }
        } else {
            failReason = reason != null ? reason : "财务系统退回";
        }

        String stored = failReason.length() > 255 ? failReason.substring(0, 255) : failReason;
        txn.setStatus(FinanceTransaction.FAILED);
        txn.setFailReason(stored);
        txn.setCallbackAt(time.now());
        txnMapper.updateById(txn);
        if ("SUBMITTED".equals(pay.getFinanceStatus()) && serialNo.equals(pay.getFinanceSerialNo())) {
            pay.setFinanceStatus("PAY_FAILED");
            pay.setFailReason(stored);
            payrollMapper.updateById(pay);
            audit.log("PAYROLL", pay.getId(), FINANCE_SYSTEM, "财务回写发放失败",
                    "流水号 " + serialNo + "：" + stored + "；经费未扣减，可重新提交。", "已提交财务", "发放失败");
        }
        return new CallbackResult(serialNo, pay.getId(), txn.getStatus(), pay.getFinanceStatus(), false);
    }

    // ------------------------------------------------------------------ 模拟回调（演示）

    /** 校级管理员“模拟财务回调”：对该薪酬单当前的流水号发起一次已签名的回调 */
    public Object simulate(String payrollId, boolean success, String reason, CurrentUser operator) {
        if (!gateway.supportsSimulation()) {
            throw BizException.badRequest("当前财务网关不支持模拟回调");
        }
        Payroll pay = scope.requireVisible(payrollMapper, scope.payrolls(operator), payrollId, "薪酬单不存在");
        if (!"SUBMITTED".equals(pay.getFinanceStatus()) || pay.getFinanceSerialNo() == null) {
            throw BizException.badRequest("该笔薪酬不处于“已提交财务”状态，无法模拟回调");
        }
        return gateway.simulateCallback(pay.getFinanceSerialNo(), success, reason);
    }

    private static String text(JsonNode body, String field) {
        JsonNode n = body.path(field);
        if (n.isMissingNode() || n.isNull()) {
            return null;
        }
        String s = n.asString().trim();
        return s.isEmpty() ? null : s;
    }
}
