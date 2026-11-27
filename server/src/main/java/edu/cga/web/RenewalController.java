package edu.cga.web;

import edu.cga.common.ApiResponse;
import edu.cga.domain.entity.Contract;
import edu.cga.security.CurrentUser;
import edu.cga.service.RenewalService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 续聘（P1）：POST /api/contracts/{id}/renew —— 岗位导师本人、校级管理员。
 */
@RestController
@RequestMapping("/api/contracts")
public class RenewalController {

    private final RenewalService renewalService;

    public RenewalController(RenewalService renewalService) {
        this.renewalService = renewalService;
    }

    @PostMapping("/{id}/renew")
    @PreAuthorize("hasAnyRole('MENTOR', 'SCHOOL_ADMIN')")
    public ApiResponse<Contract> renew(@PathVariable String id, @AuthenticationPrincipal CurrentUser user) {
        Contract c = renewalService.renew(id, user);
        return ApiResponse.ok(c, "已发起续聘，新聘期 " + c.getStartDate() + " 至 " + c.getEndDate() + "，待学生签署");
    }
}
