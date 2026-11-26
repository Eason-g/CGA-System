package edu.cga.security.login;

import java.io.IOException;
import java.util.Map;

import edu.cga.common.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

/**
 * 登录接口的 IP 限流（P2 D.2）：对 {@code app.security.login.ip-limited-paths} 中的 POST 请求计数，
 * 超限直接返回 429 JSON 和 Retry-After 头，不进入控制器。
 * <p>
 * IP 取 {@code request.getRemoteAddr()}，不信任可伪造的 X-Forwarded-For；部署在反向代理后面时
 * 应由代理（或 server.forward-headers-strategy）提供真实客户端地址。
 */
@Component
public class LoginRateLimitFilter extends OncePerRequestFilter {

    private final LoginGuard guard;
    private final LoginProtectionProperties props;
    private final JsonMapper jsonMapper;

    public LoginRateLimitFilter(LoginGuard guard, LoginProtectionProperties props, JsonMapper jsonMapper) {
        this.guard = guard;
        this.props = props;
        this.jsonMapper = jsonMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !props.getIpLimitedPaths().contains(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long retryAfter = guard.acquireIp(request.getRemoteAddr());
        if (retryAfter > 0) {
            response.setStatus(429);
            response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter));
            response.setCharacterEncoding("UTF-8");
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            jsonMapper.writeValue(response.getOutputStream(),
                    ApiResponse.fail(429, "登录请求过于频繁，请 " + retryAfter + " 秒后再试",
                            Map.of("retryAfterSeconds", retryAfter)));
            return;
        }
        chain.doFilter(request, response);
    }
}
