package edu.cga.service;

import java.util.List;
import java.util.Locale;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import edu.cga.common.BizException;
import edu.cga.crypto.FieldCrypto;
import edu.cga.domain.entity.User;
import edu.cga.domain.mapper.UserMapper;
import edu.cga.security.JwtService;
import edu.cga.security.login.LoginGuard;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 登录：account 可以是用户名、学号或邮箱（不区分大小写）；失败统一返回 401「账号或密码错误」。
 * <p>
 * P2 D.2：同一账号连续失败达到阈值后锁定，锁定期间返回 429（见 {@link LoginGuard}）；
 * P2 D.1：邮箱列是密文，按 email_hash（小写邮箱的 HMAC）检索。
 */
@Service
public class AuthService {

    public static final String LOGIN_FAILED = "账号或密码错误";

    /** 账号不存在时也做一次 BCrypt 比对，避免通过响应时间判断账号是否存在 */
    private final String dummyHash;

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final FieldCrypto fieldCrypto;
    private final LoginGuard loginGuard;

    public AuthService(UserMapper userMapper, PasswordEncoder passwordEncoder, JwtService jwtService,
                       FieldCrypto fieldCrypto, LoginGuard loginGuard) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.fieldCrypto = fieldCrypto;
        this.loginGuard = loginGuard;
        this.dummyHash = passwordEncoder.encode("cga-dummy-password");
    }

    public record LoginResult(String token, User user) {
    }

    public LoginResult login(String account, String password) {
        String q = account.trim().toLowerCase(Locale.ROOT);
        // 表使用 utf8mb4 默认排序规则（不区分大小写），LOWER 只是让意图更明确；邮箱按 HMAC 哈希等值匹配
        String emailHash = fieldCrypto.emailHash(q);
        List<User> candidates = userMapper.selectList(new QueryWrapper<User>()
                .apply("(LOWER(username) = {0} OR LOWER(student_no) = {1} OR email_hash = {2})", q, q, emailHash));
        // 锁定按用户计（用户名、学号、邮箱轮换着试也算同一账号）；查无此人时按输入计，
        // 使不存在的账号同样会“锁定”，不能通过 429 与 401 的差别判断账号是否存在
        User user = candidates.size() == 1 ? candidates.get(0) : null;
        String lockKey = user == null ? "A:" + q : userLockKey(user);
        loginGuard.ensureNotLocked(lockKey);
        String hash = user == null ? null : user.getPasswordHash();
        boolean matches = passwordEncoder.matches(password, hash == null || hash.isEmpty() ? dummyHash : hash);
        if (hash != null && !hash.isEmpty() && matches) {
            loginGuard.recordSuccess(lockKey);
            return new LoginResult(jwtService.issue(user), user);
        }
        loginGuard.recordFailure(lockKey);
        throw new BizException(401, LOGIN_FAILED);
    }

    /**
     * 统一身份认证登录入口（P2 B.1）：票据已由 SsoProvider 校验，这里只按学工号（用户名或学号，不区分大小写）
     * 映射本系统用户并签发 JWT；匹配不到返回 403。审计日志由调用方 SsoService 记录。
     */
    public LoginResult loginBySso(String uid) {
        User user = resolveSsoUser(normalizeSsoUid(uid));
        if (user == null) {
            throw BizException.forbidden("统一认证账号未在本系统开通");
        }
        String lockKey = userLockKey(user);
        loginGuard.ensureNotLocked(lockKey);
        loginGuard.recordSuccess(lockKey);
        return new LoginResult(jwtService.issue(user), user);
    }

    public String ssoLockKey(String uid) {
        String value = normalizeSsoUid(uid);
        User user = resolveSsoUser(value);
        return user == null ? "CAS:" + value : userLockKey(user);
    }

    private User resolveSsoUser(String uid) {
        if (uid.isEmpty()) {
            return null;
        }
        List<User> candidates = userMapper.selectList(new QueryWrapper<User>()
                .apply("(LOWER(username) = {0} OR LOWER(student_no) = {1})", uid, uid).last("LIMIT 2"));
        return candidates.size() == 1 ? candidates.get(0) : null;
    }

    private static String normalizeSsoUid(String uid) {
        return uid == null ? "" : uid.trim().toLowerCase(Locale.ROOT);
    }

    private static String userLockKey(User user) {
        return "U:" + user.getId();
    }

    public User currentUser(String userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(401, "未登录或登录已失效");
        }
        return user;
    }
}
