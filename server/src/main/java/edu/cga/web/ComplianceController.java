package edu.cga.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import edu.cga.common.ApiResponse;
import edu.cga.security.CurrentUser;
import edu.cga.service.ComplianceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 合规核验（P1 第三批 3.2）：国际生办、校级管理员。
 */
@RestController
public class ComplianceController {

    private final ComplianceService complianceService;

    public ComplianceController(ComplianceService complianceService) {
        this.complianceService = complianceService;
    }

    public record VerifyBody(
            @Size(max = 32, message = "附件 ID 不合法") String attachmentId,
            @NotBlank(message = "请选择核验结论")
            @Pattern(regexp = "APPROVE|REJECT", message = "decision 取值必须为 APPROVE 或 REJECT") String decision,
            BigDecimal languageLevel,
            String languageCert,
            LocalDate visaExpiry,
            String visaType,
            @Size(max = 255, message = "核验意见不能超过 255 字") String opinion) {
    }

    /** GET /api/compliance/materials?status=PENDING：合规核验列表 */
    @GetMapping("/api/compliance/materials")
    @PreAuthorize("hasAnyRole('INTL_OFFICER', 'SCHOOL_ADMIN')")
    public ApiResponse<List<Map<String, Object>>> list(@RequestParam(required = false) String status,
                                                       @AuthenticationPrincipal CurrentUser user) {
        return ApiResponse.ok(complianceService.list(status, user));
    }

    /** POST /api/users/{id}/compliance-verify：核验语言证明或签证材料 */
    @PostMapping("/api/users/{id}/compliance-verify")
    @PreAuthorize("hasAnyRole('INTL_OFFICER', 'SCHOOL_ADMIN')")
    public ApiResponse<ComplianceService.VerifyResult> verify(@PathVariable String id, @Valid @RequestBody VerifyBody b,
                                                              @AuthenticationPrincipal CurrentUser user) {
        var result = complianceService.verify(id, new ComplianceService.VerifyRequest(b.attachmentId(), b.decision(),
                b.languageLevel(), b.languageCert(), b.visaExpiry(), b.visaType(), b.opinion()), user);
        boolean approve = "APPROVE".equals(b.decision());
        return ApiResponse.ok(result, approve ? "核验通过，学籍合规信息已更新" : "已记录核验不通过，并通知学生");
    }
}
