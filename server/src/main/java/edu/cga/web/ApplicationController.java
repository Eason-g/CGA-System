package edu.cga.web;

import edu.cga.common.ApiResponse;
import edu.cga.domain.entity.JobApplication;
import edu.cga.security.CurrentUser;
import edu.cga.service.ApplicationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 岗位申请：对应 api.js 的 submitApplication、reviewSpecialApproval、scheduleInterview、decideApplication。
 */
@RestController
@RequestMapping("/api/applications")
public class ApplicationController {

    private final ApplicationService applicationService;

    public ApplicationController(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    public record SubmitRequest(
            @NotBlank(message = "请选择要申请的岗位") String jobId,
            @Size(max = 2000, message = "申请陈述不能超过 2000 字") String statement,
            @Size(max = 128, message = "简历文件名过长") String resumeFileName,
            Boolean isSpecialApproval,
            @Size(max = 2000, message = "特批理由不能超过 2000 字") String specialReason) {
    }

    public record SpecialReviewRequest(
            @NotBlank(message = "请选择审批结论")
            @Pattern(regexp = "APPROVE|REJECT", message = "decision 取值必须为 APPROVE 或 REJECT") String decision,
            String opinion) {
    }

    public record InterviewRequest(
            @NotBlank(message = "请填写面试时间") String time,
            @NotBlank(message = "请填写面试地点") String location,
            String note) {
    }

    public record DecisionRequest(
            @NotNull(message = "请填写面试评分") @Min(value = 0, message = "评分必须在 0–100 之间")
            @Max(value = 100, message = "评分必须在 0–100 之间") Integer score,
            @NotBlank(message = "请选择选拔结论")
            @Pattern(regexp = "HIRE|REJECT", message = "decision 取值必须为 HIRE 或 REJECT") String decision,
            String comment) {
    }

    /** POST /api/applications：学生本人 */
    @PostMapping
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<JobApplication> submit(@Valid @RequestBody SubmitRequest req,
                                              @AuthenticationPrincipal CurrentUser user) {
        boolean special = Boolean.TRUE.equals(req.isSpecialApproval());
        JobApplication app = applicationService.submit(req.jobId(), req.statement(), req.resumeFileName(), special,
                req.specialReason(), user);
        return ApiResponse.ok(app, special ? "已提交特批申请，等待国际生办审核留痕" : "投递成功，等待导师筛选");
    }

    /** POST /api/applications/{id}/special-review：国际生办、校级管理员 */
    @PostMapping("/{id}/special-review")
    @PreAuthorize("hasAnyRole('INTL_OFFICER', 'SCHOOL_ADMIN')")
    public ApiResponse<JobApplication> specialReview(@PathVariable String id,
                                                     @Valid @RequestBody SpecialReviewRequest req,
                                                     @AuthenticationPrincipal CurrentUser user) {
        JobApplication app = applicationService.reviewSpecial(id, req.decision(), req.opinion(), user);
        return ApiResponse.ok(app, "APPROVE".equals(req.decision()) ? "特批已通过并流转至导师选拔" : "已驳回特批申请");
    }

    /** POST /api/applications/{id}/interview：岗位导师本人、校级管理员 */
    @PostMapping("/{id}/interview")
    @PreAuthorize("hasAnyRole('MENTOR', 'SCHOOL_ADMIN')")
    public ApiResponse<JobApplication> interview(@PathVariable String id, @Valid @RequestBody InterviewRequest req,
                                                 @AuthenticationPrincipal CurrentUser user) {
        JobApplication app = applicationService.scheduleInterview(id, req.time(), req.location(), req.note(), user);
        return ApiResponse.ok(app, "已安排面试并生成站内信与模拟邮件通知");
    }

    /** POST /api/applications/{id}/decision：岗位导师本人、校级管理员；HIRE 走录用复核 */
    @PostMapping("/{id}/decision")
    @PreAuthorize("hasAnyRole('MENTOR', 'SCHOOL_ADMIN')")
    public ApiResponse<JobApplication> decide(@PathVariable String id, @Valid @RequestBody DecisionRequest req,
                                              @AuthenticationPrincipal CurrentUser user) {
        JobApplication app = applicationService.decide(id, req.score(), req.decision(), req.comment(), user);
        return ApiResponse.ok(app, "HIRE".equals(req.decision()) ? "已录用并自动下发电子聘用协议与通知" : "已更新选拔结果");
    }
}
