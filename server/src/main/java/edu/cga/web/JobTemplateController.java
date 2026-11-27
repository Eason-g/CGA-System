package edu.cga.web;

import java.util.List;
import java.util.Map;

import edu.cga.common.ApiResponse;
import edu.cga.domain.entity.JobTemplate;
import edu.cga.security.CurrentUser;
import edu.cga.service.JobTemplateService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 岗位模板（P1）：GET / POST /api/job-templates，DELETE /api/job-templates/{id}。
 */
@RestController
@RequestMapping("/api/job-templates")
@PreAuthorize("hasAnyRole('MENTOR', 'DEPT_ADMIN', 'SCHOOL_ADMIN')")
public class JobTemplateController {

    private final JobTemplateService templateService;

    public JobTemplateController(JobTemplateService templateService) {
        this.templateService = templateService;
    }

    /** global：是否全校通用（仅校级管理员）；deptId：校级管理员保存院系模板时指定院系 */
    public record TemplateRequest(String name, String type, Boolean global, String deptId,
                                  Map<String, Object> payload) {
    }

    @GetMapping
    public ApiResponse<List<JobTemplate>> list(@AuthenticationPrincipal CurrentUser user) {
        return ApiResponse.ok(templateService.list(user));
    }

    @PostMapping
    public ApiResponse<JobTemplate> create(@RequestBody TemplateRequest req, @AuthenticationPrincipal CurrentUser user) {
        JobTemplate t = templateService.create(req.name(), req.type(), Boolean.TRUE.equals(req.global()), req.deptId(),
                req.payload(), user);
        return ApiResponse.ok(t, "已存为模板「" + t.getName() + "」");
    }

    @DeleteMapping("/{id}")
    public ApiResponse<JobTemplate> delete(@PathVariable String id, @AuthenticationPrincipal CurrentUser user) {
        JobTemplate t = templateService.delete(id, user);
        return ApiResponse.ok(t, "已删除模板「" + t.getName() + "」");
    }
}
