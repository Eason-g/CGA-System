package edu.cga.web;

import java.util.Map;

import edu.cga.common.ApiResponse;
import edu.cga.service.FeedbackRateLimiter;
import edu.cga.service.FeedbackService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 公开评价接口（无需登录，SecurityConfig 放行 /api/public/**）：服务对象凭评价码查询与提交。
 * 每次请求先按“IP + 评价码”限流（{@link FeedbackRateLimiter}），超限 429。
 */
@RestController
@RequestMapping("/api/public/feedback")
public class PublicFeedbackController {

    private final FeedbackService feedbackService;
    private final FeedbackRateLimiter limiter;

    public PublicFeedbackController(FeedbackService feedbackService, FeedbackRateLimiter limiter) {
        this.feedbackService = feedbackService;
        this.limiter = limiter;
    }

    /** clientId：浏览器生成并存在 localStorage 的随机 ID，后端只存它的 SHA-256 */
    public record SubmitRequest(Integer rating, String comment, String clientId) {
    }

    @GetMapping("/{code}")
    public ApiResponse<Map<String, Object>> info(@PathVariable String code, HttpServletRequest request) {
        limiter.checkQuery(request.getRemoteAddr(), limitKey(code));
        return ApiResponse.ok(feedbackService.publicInfo(code));
    }

    @PostMapping("/{code}")
    public ApiResponse<Map<String, Object>> submit(@PathVariable String code, @RequestBody SubmitRequest req,
                                                   HttpServletRequest request) {
        limiter.checkSubmit(request.getRemoteAddr(), limitKey(code));
        return ApiResponse.ok(feedbackService.publicSubmit(code, req.rating(), req.comment(), req.clientId()),
                "评价已提交，感谢您的反馈");
    }

    /** 限流键：规范化后的评价码（截断到 16 位，避免超长路径撑大计数表） */
    private static String limitKey(String code) {
        String c = code == null ? "" : code.trim().toUpperCase();
        return c.length() > 16 ? c.substring(0, 16) : c;
    }
}
