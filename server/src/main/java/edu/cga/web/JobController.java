package edu.cga.web;

import java.util.Map;

import edu.cga.common.ApiResponse;
import edu.cga.domain.entity.Job;
import edu.cga.security.CurrentUser;
import edu.cga.service.JobService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 岗位：对应 api.js 的 createJob、reviewJob、toggleMilestone。
 */
@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    public record ReviewRequest(
            @NotBlank(message = "请选择审批结论")
            @Pattern(regexp = "APPROVE|REJECT", message = "decision 取值必须为 APPROVE 或 REJECT") String decision,
            String opinion) {
    }

    /** POST /api/jobs：导师、院系管理员只能为本院系建岗；校级管理员不限 */
    @PostMapping
    @PreAuthorize("hasAnyRole('MENTOR', 'DEPT_ADMIN', 'SCHOOL_ADMIN')")
    public ApiResponse<Job> create(@Valid @RequestBody JobService.JobDraft req,
                                   @AuthenticationPrincipal CurrentUser user) {
        return ApiResponse.ok(jobService.createJob(req, user), "岗位已提交并进入审批流");
    }

    /**
     * POST /api/jobs/{id}/review：当前流程节点的角色才可审，校级管理员可代审。
     * 不在这里限制角色：先按数据范围取岗位（外院岗位 404），再由 Service 判断节点角色（403）。
     */
    @PostMapping("/{id}/review")
    public ApiResponse<Job> review(@PathVariable String id, @Valid @RequestBody ReviewRequest req,
                                   @AuthenticationPrincipal CurrentUser user) {
        Job job = jobService.reviewJob(id, req.decision(), req.opinion(), user);
        return ApiResponse.ok(job, "REJECT".equals(req.decision()) ? "已驳回该岗位申请" : "审批通过并已流转");
    }

    /** POST /api/jobs/{id}/milestones/{msId}/toggle：岗位导师本人、校级管理员 */
    @PostMapping("/{id}/milestones/{msId}/toggle")
    @PreAuthorize("hasAnyRole('MENTOR', 'SCHOOL_ADMIN')")
    public ApiResponse<Map<String, Object>> toggleMilestone(@PathVariable String id, @PathVariable String msId,
                                                            @AuthenticationPrincipal CurrentUser user) {
        return ApiResponse.ok(jobService.toggleMilestone(id, msId, user), "已更新里程碑状态");
    }
}
