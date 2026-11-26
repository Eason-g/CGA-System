package edu.cga.web;

import java.util.Map;

import edu.cga.common.ApiResponse;
import edu.cga.domain.entity.Evaluation;
import edu.cga.security.CurrentUser;
import edu.cga.service.EvaluationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 周期考核：对应 api.js 的 submitEvaluation。
 */
@RestController
@RequestMapping("/api/evaluations")
public class EvaluationController {

    private final EvaluationService evaluationService;

    public EvaluationController(EvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    public record EvaluationRequest(
            @NotBlank(message = "请选择要考核的聘用协议") String contractId,
            @Size(max = 64, message = "考核周期名称过长") String period,
            Map<String, Object> dimensionScores,
            @Size(max = 2000, message = "考核评语不能超过 2000 字") String comment,
            Map<String, Object> courseEvalSync) {
    }

    /** POST /api/evaluations：岗位导师本人、本院系管理员、校级管理员 */
    @PostMapping
    @PreAuthorize("hasAnyRole('MENTOR', 'DEPT_ADMIN', 'SCHOOL_ADMIN')")
    public ApiResponse<Evaluation> submit(@Valid @RequestBody EvaluationRequest req,
                                          @AuthenticationPrincipal CurrentUser user) {
        EvaluationService.EvaluationResult r = evaluationService.submit(req.contractId(), req.period(),
                req.dimensionScores(), req.comment(), req.courseEvalSync(), user);
        return ApiResponse.ok(r.evaluation(), r.message());
    }
}
