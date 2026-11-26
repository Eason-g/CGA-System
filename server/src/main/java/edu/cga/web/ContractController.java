package edu.cga.web;

import edu.cga.common.ApiResponse;
import edu.cga.domain.entity.Contract;
import edu.cga.security.CurrentUser;
import edu.cga.service.ContractService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 电子聘用协议：对应 api.js 的 signContract。
 */
@RestController
@RequestMapping("/api/contracts")
public class ContractController {

    private final ContractService contractService;

    public ContractController(ContractService contractService) {
        this.contractService = contractService;
    }

    public record SignRequest(String signatureDataUrl, Boolean complianceAgreed) {
    }

    /** POST /api/contracts/{id}/sign：合同所属学生本人；状态须为 PENDING_SIGN */
    @PostMapping("/{id}/sign")
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<Contract> sign(@PathVariable String id, @RequestBody SignRequest req,
                                      @AuthenticationPrincipal CurrentUser user) {
        Contract con = contractService.sign(id, req.signatureDataUrl(), Boolean.TRUE.equals(req.complianceAgreed()),
                user);
        return ApiResponse.ok(con, "电子聘用协议签署成功，已正式生效");
    }
}
