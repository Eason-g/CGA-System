package edu.cga.service;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import edu.cga.common.BizException;
import edu.cga.domain.entity.User;
import edu.cga.domain.mapper.UserMapper;
import edu.cga.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 登录：account 可以是用户名、学号或邮箱（不区分大小写）；失败统一返回 401「账号或密码错误」。
 */
@Service
public class AuthService {

    public static final String LOGIN_FAILED = "账号或密码错误";

    /** 账号不存在时也做一次 BCrypt 比对，避免通过响应时间判断账号是否存在 */
    private final String dummyHash;

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserMapper userMapper, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.dummyHash = passwordEncoder.encode("cga-dummy-password");
    }

    public record LoginResult(String token, User user) {
    }

    public LoginResult login(String account, String password) {
        String q = account.trim().toLowerCase();
        // 表使用 utf8mb4 默认排序规则（不区分大小写），LOWER 只是让意图更明确
        List<User> candidates = userMapper.selectList(new QueryWrapper<User>()
                .apply("(LOWER(username) = {0} OR LOWER(student_no) = {1} OR LOWER(email) = {2})", q, q, q));
        for (User user : candidates) {
            String hash = user.getPasswordHash();
            if (hash != null && !hash.isEmpty() && passwordEncoder.matches(password, hash)) {
                return new LoginResult(jwtService.issue(user), user);
            }
        }
        if (candidates.isEmpty()) {
            passwordEncoder.matches(password, dummyHash);
        }
        throw new BizException(401, LOGIN_FAILED);
    }

    public User currentUser(String userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(401, "未登录或登录已失效");
        }
        return user;
    }
}
