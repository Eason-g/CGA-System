package edu.cga.web;

import java.util.Map;

import edu.cga.common.ApiResponse;
import edu.cga.security.CurrentUser;
import edu.cga.service.RecommendationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 智能推荐（P2 C.2）：学生端岗位大厅“为你推荐”与“技能推荐”排序。
 */
@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {

    private final RecommendationService recommendationService;

    public RecommendationController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    /** GET /api/recommendations：仅学生本人；其他角色 403 */
    @GetMapping
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<Map<String, Object>> recommend(@AuthenticationPrincipal CurrentUser user) {
        return ApiResponse.ok(recommendationService.recommend(user), "已生成岗位推荐");
    }
}
