package edu.cga.web;

import edu.cga.common.ApiResponse;
import edu.cga.domain.entity.Payroll;
import edu.cga.security.CurrentUser;
import edu.cga.service.PayrollService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 薪酬：对应 api.js 的 generateMonthlyPayrolls、confirmPayrollPayout。
 */
@RestController
@RequestMapping("/api/payrolls")
public class PayrollController {

    private final PayrollService payrollService;

    public PayrollController(PayrollService payrollService) {
        this.payrollService = payrollService;
    }

    public record GenerateRequest(String month) {
    }

    /**
     * POST /api/payrolls/generate：院系管理员（只核算本院系）、校级管理员；month 为空取当前月份。
     * data 为 { count, blocked: [{ contractId, studentId, amount, reason }] }。
     */
    @PostMapping("/generate")
    @PreAuthorize("hasAnyRole('DEPT_ADMIN', 'SCHOOL_ADMIN')")
    public ApiResponse<PayrollService.GenerateResult> generate(@RequestBody(required = false) GenerateRequest req,
                                                               @AuthenticationPrincipal CurrentUser user) {
        PayrollService.GenerateResult result = payrollService.generate(req == null ? null : req.month(), user);
        return ApiResponse.ok(result, "已完成 " + result.month() + " 薪酬自动核算（更新 " + result.count() + " 笔，预算拦截 "
                + result.blocked().size() + " 笔）");
    }

    /** POST /api/payrolls/{id}/payout：校级管理员（兼财务） */
    @PostMapping("/{id}/payout")
    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    public ApiResponse<Payroll> payout(@PathVariable String id, @AuthenticationPrincipal CurrentUser user) {
        Payroll pay = payrollService.payout(id, user);
        return ApiResponse.ok(pay, "财务打款成功（凭证 " + pay.getVoucherNo() + "），经费池余额已实时扣减");
    }
}
