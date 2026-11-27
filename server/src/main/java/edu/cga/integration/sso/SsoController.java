package edu.cga.integration.sso;

import java.util.Map;

import edu.cga.common.ApiResponse;
import edu.cga.service.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 统一身份认证接口（公开）：
 * <ul>
 *   <li>GET /api/auth/sso/login-url?service=…：返回认证中心登录地址；</li>
 *   <li>POST /api/auth/sso/exchange {ticket, service}：换取本系统 JWT，返回体与密码登录相同 { token, user }。</li>
 * </ul>
 * 票据无效、过期、重复使用、service 不一致 401；账号未开通 403；SSO 未启用 404。
 */
@RestController
@RequestMapping("/api/auth/sso")
public class SsoController {

    private final SsoService sso;

    public SsoController(SsoService sso) {
        this.sso = sso;
    }

    public record ExchangeRequest(
            @NotBlank(message = "缺少统一认证票据") String ticket,
            @NotBlank(message = "缺少 service 参数") String service) {
    }

    @GetMapping("/login-url")
    public ApiResponse<Map<String, String>> loginUrl(@RequestParam(required = false) String service) {
        return ApiResponse.ok(Map.of("loginUrl", sso.loginUrl(service)));
    }

    @PostMapping("/exchange")
    public ApiResponse<AuthService.LoginResult> exchange(@Valid @RequestBody ExchangeRequest req) {
        AuthService.LoginResult result = sso.exchange(req.ticket(), req.service());
        return ApiResponse.ok(result, "欢迎回来，" + result.user().getName() + "（统一身份认证）");
    }
}
