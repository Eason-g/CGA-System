package edu.cga.web;

import edu.cga.common.ApiResponse;
import edu.cga.service.AuditChainService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 审计日志防篡改校验（P1 第二批 2.2）。
 */
@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditChainService auditChain;

    public AuditController(AuditChainService auditChain) {
        this.auditChain = auditChain;
    }

    /**
     * GET /api/audit/verify：校级管理员。从第一条起重算哈希链，
     * data 为 { ok, checked, brokenAt: { id, seq, reason, detail } | null }。
     */
    @GetMapping("/verify")
    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    public ApiResponse<AuditChainService.VerifyResult> verify() {
        AuditChainService.VerifyResult r = auditChain.verify();
        String msg = r.ok()
                ? "审计日志完整性校验通过，共校验 " + r.checked() + " 条"
                : "发现审计日志异常：" + r.brokenAt().get("reason")
                        + (r.brokenAt().get("id") != null ? "（日志 " + r.brokenAt().get("id") + "）" : "");
        return ApiResponse.ok(r, msg);
    }
}
