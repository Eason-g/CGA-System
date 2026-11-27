package edu.cga.web;

import edu.cga.common.ApiResponse;
import edu.cga.domain.entity.Payroll;
import edu.cga.finance.FinanceSignature;
import edu.cga.security.CurrentUser;
import edu.cga.service.FinanceService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * 财务对接（P1 第三批 3.3）：提交财务、财务回调（公开，签名校验）、模拟财务回调（演示）。
 * 原一步发放 POST /api/payrolls/{id}/payout 保留在 PayrollController。
 */
@RestController
public class FinanceController {

    private final FinanceService financeService;

    public FinanceController(FinanceService financeService) {
        this.financeService = financeService;
    }

    public record SimulateRequest(Boolean success, String reason) {
    }

    /** POST /api/payrolls/{id}/submit-finance：校级管理员（兼财务） */
    @PostMapping("/api/payrolls/{id}/submit-finance")
    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    public ApiResponse<Payroll> submit(@PathVariable String id, @AuthenticationPrincipal CurrentUser user) {
        Payroll pay = financeService.submit(id, user);
        return ApiResponse.ok(pay, "已提交财务（流水号 " + pay.getFinanceSerialNo() + "），等待财务系统回写结果");
    }

    /**
     * POST /api/integration/finance/callback：学校财务系统回调（公开接口，不带登录令牌）。
     * 请求头 X-Finance-Signature = HMAC-SHA256(app.finance.callback-secret, 原始请求体)，签名错误返回 401。
     * 请求体 { serialNo, success, voucherNo, reason, timestamp }；同一流水号重复回调只处理一次。
     */
    @PostMapping("/api/integration/finance/callback")
    public ApiResponse<FinanceService.CallbackResult> callback(
            @RequestBody(required = false) String rawBody,
            @RequestHeader(value = FinanceSignature.HEADER, required = false) String signature) {
        FinanceService.CallbackResult r = financeService.handleCallback(rawBody == null ? "" : rawBody, signature);
        String message = "RECONCILE".equals(r.transactionStatus()) ? "已收到成功回执，内部记账待核对"
                : r.duplicate() ? "该流水号已处理，忽略重复回调" : "回调已处理";
        return ApiResponse.ok(r, message);
    }

    /** POST /api/payrolls/{id}/simulate-finance-callback { success, reason }：校级管理员，演示失败重试用 */
    @PostMapping("/api/payrolls/{id}/simulate-finance-callback")
    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    public ApiResponse<Object> simulate(@PathVariable String id, @RequestBody(required = false) SimulateRequest req,
                                        @AuthenticationPrincipal CurrentUser user) {
        boolean success = req == null || !Boolean.FALSE.equals(req.success());
        Object r = financeService.simulate(id, success, req == null ? null : req.reason(), user);
        String message = r instanceof FinanceService.CallbackResult result && "RECONCILE".equals(result.transactionStatus())
                ? "已模拟成功回执，内部记账待核对" : success ? "已模拟财务回写：发放成功" : "已模拟财务回写：发放失败";
        return ApiResponse.ok(r, message);
    }
}
