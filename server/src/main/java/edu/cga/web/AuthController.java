package edu.cga.web;

import edu.cga.common.ApiResponse;
import edu.cga.domain.entity.User;
import edu.cga.security.CurrentUser;
import edu.cga.service.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口：对应 api.js 的 loginByCredentials。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    public record LoginRequest(
            @NotBlank(message = "请输入学工号、用户名或校园邮箱") String account,
            @NotBlank(message = "请输入密码") String password) {
    }

    /** POST /api/auth/login：公开接口，成功返回 { token, user } */
    @PostMapping("/login")
    public ApiResponse<AuthService.LoginResult> login(@Valid @RequestBody LoginRequest req) {
        AuthService.LoginResult result = authService.login(req.account(), req.password());
        return ApiResponse.ok(result, "欢迎回来，" + result.user().getName());
    }

    /** GET /api/auth/me：返回当前登录用户 */
    @GetMapping("/me")
    public ApiResponse<User> me(@AuthenticationPrincipal CurrentUser user) {
        return ApiResponse.ok(authService.currentUser(user.id()));
    }
}
