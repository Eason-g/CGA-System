package edu.cga.web;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import edu.cga.common.ApiResponse;
import edu.cga.security.CurrentUser;
import edu.cga.service.FeedbackService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 服务对象评价（系统内，需登录）：
 * <ul>
 *   <li>GET /api/feedback-rounds?contractId= ：评价轮列表（含评价码与单条评价）——岗位导师、院系管理员、校级管理员；</li>
 *   <li>POST /api/feedback-rounds {contractId, deadline}：开启评价轮；</li>
 *   <li>POST /api/feedback-rounds/{id}/close：关闭评价轮；</li>
 *   <li>GET /api/contracts/{id}/feedback-summary：条数与平均分（助理本人也可查看，看不到单条评价）。</li>
 * </ul>
 */
@RestController
public class FeedbackController {

    private final FeedbackService feedbackService;

    public FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    public record OpenRoundRequest(@NotBlank(message = "请选择聘用协议") String contractId, LocalDate deadline) {
    }

    @GetMapping("/api/feedback-rounds")
    @PreAuthorize("hasAnyRole('MENTOR', 'DEPT_ADMIN', 'SCHOOL_ADMIN')")
    public ApiResponse<List<Map<String, Object>>> list(@RequestParam(required = false) String contractId,
                                                       @AuthenticationPrincipal CurrentUser user) {
        return ApiResponse.ok(feedbackService.listRounds(contractId, user));
    }

    @PostMapping("/api/feedback-rounds")
    @PreAuthorize("hasAnyRole('MENTOR', 'DEPT_ADMIN', 'SCHOOL_ADMIN')")
    public ApiResponse<Map<String, Object>> open(@Valid @RequestBody OpenRoundRequest req,
                                                 @AuthenticationPrincipal CurrentUser user) {
        Map<String, Object> round = feedbackService.open(req.contractId(), req.deadline(), user);
        return ApiResponse.ok(round, "已开启服务评价，评价码 " + round.get("code"));
    }

    @PostMapping("/api/feedback-rounds/{id}/close")
    @PreAuthorize("hasAnyRole('MENTOR', 'DEPT_ADMIN', 'SCHOOL_ADMIN')")
    public ApiResponse<Map<String, Object>> close(@PathVariable String id, @AuthenticationPrincipal CurrentUser user) {
        return ApiResponse.ok(feedbackService.close(id, user), "已关闭本轮服务评价");
    }

    @GetMapping("/api/contracts/{id}/feedback-summary")
    public ApiResponse<Map<String, Object>> summary(@PathVariable String id, @AuthenticationPrincipal CurrentUser user) {
        return ApiResponse.ok(feedbackService.summary(id, user).toMap(id));
    }
}
