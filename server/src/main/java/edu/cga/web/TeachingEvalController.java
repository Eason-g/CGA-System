package edu.cga.web;

import java.util.Map;

import edu.cga.common.ApiResponse;
import edu.cga.common.BizException;
import edu.cga.security.CurrentUser;
import edu.cga.service.TeachingEvalService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 评教联动（P2 C.1）：考核页“同步评教结果”。
 */
@RestController
@RequestMapping("/api/integration/teaching-eval")
public class TeachingEvalController {

    private final TeachingEvalService teachingEvalService;

    public TeachingEvalController(TeachingEvalService teachingEvalService) {
        this.teachingEvalService = teachingEvalService;
    }

    /**
     * GET /api/integration/teaching-eval?contractId=：岗位导师本人、本院系管理员、校级管理员。
     * 课程没有评教数据时仍返回 200，available=false、message=“该课程暂无评教数据”；非 TA 合同 400。
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('MENTOR', 'DEPT_ADMIN', 'SCHOOL_ADMIN')")
    public ApiResponse<Map<String, Object>> query(@RequestParam(required = false) String contractId,
                                                  @AuthenticationPrincipal CurrentUser user) {
        if (contractId == null || contractId.isBlank()) {
            throw BizException.badRequest("请选择要同步评教结果的聘用协议");
        }
        Map<String, Object> data = teachingEvalService.query(contractId.trim(), user);
        String message = Boolean.TRUE.equals(data.get("available"))
                ? "已从" + teachingEvalService.sourceName() + "获取评教结果"
                : String.valueOf(data.get("message"));
        return ApiResponse.ok(data, message);
    }
}
