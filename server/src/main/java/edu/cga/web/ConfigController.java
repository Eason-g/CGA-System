package edu.cga.web;

import java.util.List;
import java.util.Map;

import edu.cga.common.ApiResponse;
import edu.cga.security.CurrentUser;
import edu.cga.service.ConfigService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 配置中心：对应 api.js 的 saveConfigs。
 */
@RestController
@RequestMapping("/api/configs")
public class ConfigController {

    private final ConfigService configService;

    public ConfigController(ConfigService configService) {
        this.configService = configService;
    }

    /** 各项都可省略，省略的不修改；jobTypeSchemas 为 P1 新增的岗位类型差异化字段 */
    public record ConfigRequest(Map<String, List<Map<String, Object>>> workflows,
                                Map<String, List<Map<String, Object>>> evaluationTemplates,
                                Map<String, Object> globalRules,
                                Map<String, Object> jobTypeSchemas) {
    }

    /** PUT /api/configs：校级管理员 */
    @PutMapping
    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    public ApiResponse<Map<String, Object>> save(@RequestBody ConfigRequest req,
                                                 @AuthenticationPrincipal CurrentUser user) {
        Map<String, Object> configs = configService.save(req.workflows(), req.evaluationTemplates(),
                req.globalRules(), req.jobTypeSchemas(), user);
        return ApiResponse.ok(configs, "配置已保存并即时生效");
    }
}
