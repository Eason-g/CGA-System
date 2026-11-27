package edu.cga.security.login;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import edu.cga.common.BizException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 登录防护（P2 D.2）：账号连续失败锁定 + 同一 IP 登录请求限流。
 * <ul>
 *   <li>账号：{@code failureWindow}（默认 15 分钟）内连续失败 {@code maxFailures}（默认 5）次，锁定
 *       {@code lockDuration}（默认 15 分钟）；锁定期间即使密码正确也返回 429 并提示剩余时间；登录成功清零。</li>
 *   <li>IP：滑动窗口，任意连续 60 秒内请求数不超过 {@code ipMaxPerMinute}；超出返回 429。</li>
 * </ul>
 * 计数放在进程内（ConcurrentHashMap），单实例部署够用，多实例需换成 Redis 等共享存储；重启后计数清零。
 * 每个 key 的读改写都在 {@code ConcurrentHashMap.compute} 或该 key 自己的锁里完成，线程安全。
 * 过期条目在表变大时顺带清理，避免长期运行后无限增长。
 */
@Component
public class LoginGuard {

    private static final int PURGE_THRESHOLD = 10_000;
    private static final long MINUTE_MILLIS = 60_000L;

    private final LoginProtectionProperties props;
    private final Clock clock;

    private final Map<String, AccountState> accounts = new ConcurrentHashMap<>();
    private final Map<String, Deque<Long>> ips = new ConcurrentHashMap<>();

    @Autowired
    public LoginGuard(LoginProtectionProperties props) {
        this(props, Clock.systemUTC());
    }

    /** 单元测试可传入可调的时钟 */
    LoginGuard(LoginProtectionProperties props, Clock clock) {
        this.props = props;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ 账号锁定

    /** 账号处于锁定期时抛出 429；应在校验密码之前调用 */
    public void ensureNotLocked(String accountKey) {
        AccountState s = accounts.get(accountKey);
        if (s == null) {
            return;
        }
        long now = clock.millis();
        long until;
        synchronized (s) {
            until = s.lockedUntil;
        }
        if (until > now) {
            throw locked(until - now);
        }
    }

    /**
     * 记录一次密码错误。达到阈值时开始锁定并抛出 429（本次就提示已锁定，而不是等下一次再告知）；
     * 否则正常返回，由调用方返回 401。
     */
    public void recordFailure(String accountKey) {
        long now = clock.millis();
        long windowMs = props.getFailureWindow().toMillis();
        purgeIfLarge(now);
        AccountState s = accounts.computeIfAbsent(accountKey, k -> new AccountState());
        long lockedUntil = 0;
        synchronized (s) {
            if (s.lockedUntil > now) {
                lockedUntil = s.lockedUntil;
            } else {
                // 上一次失败已超出窗口则重新计数（“15 分钟内连续失败”）
                if (s.failures == 0 || now - s.firstFailureAt > windowMs) {
                    s.failures = 0;
                    s.firstFailureAt = now;
                }
                s.failures++;
                s.lastTouched = now;
                if (s.failures >= props.getMaxFailures()) {
                    s.lockedUntil = now + props.getLockDuration().toMillis();
                    s.failures = 0;
                    lockedUntil = s.lockedUntil;
                }
            }
        }
        if (lockedUntil > now) {
            throw locked(lockedUntil - now);
        }
    }

    /** 登录成功：清零失败计数 */
    public void recordSuccess(String accountKey) {
        accounts.remove(accountKey);
    }

    /** 清空全部计数（演示数据重置、测试用） */
    public void reset() {
        accounts.clear();
        ips.clear();
    }

    private static BizException locked(long remainingMs) {
        long seconds = Math.max(1, (remainingMs + 999) / 1000);
        long minutes = (seconds + 59) / 60;
        return new BizException(429, "登录失败次数过多，账号已临时锁定，请 " + minutes + " 分钟后再试",
                "Too many failed login attempts; the account is temporarily locked. Please try again in " + minutes
                        + " minute(s)",
                Map.of("retryAfterSeconds", seconds));
    }

    // ------------------------------------------------------------------ IP 限流

    /**
     * 计入一次来自该 IP 的登录请求。
     *
     * @return 0 表示放行；大于 0 表示已超限，值为建议的重试等待秒数
     */
    public long acquireIp(String ip) {
        long now = clock.millis();
        int limit = props.getIpMaxPerMinute();
        purgeIfLarge(now);
        Deque<Long> q = ips.computeIfAbsent(ip == null ? "unknown" : ip, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && now - q.peekFirst() >= MINUTE_MILLIS) {
                q.pollFirst();
            }
            if (q.size() >= limit) {
                // 被拒绝的请求不入队，否则持续请求会让窗口永远满着
                return Math.max(1, (MINUTE_MILLIS - (now - q.peekFirst()) + 999) / 1000);
            }
            q.addLast(now);
            return 0;
        }
    }

    /** 与 {@link #acquireIp} 相同，超限时直接抛 429（供统一认证换票等代码路径调用） */
    public void checkIp(String ip) {
        long retry = acquireIp(ip);
        if (retry > 0) {
            throw new BizException(429, "登录请求过于频繁，请稍后再试", "Too many login requests; please try again later",
                    Map.of("retryAfterSeconds", retry));
        }
    }

    // ------------------------------------------------------------------ 清理

    private void purgeIfLarge(long now) {
        if (accounts.size() > PURGE_THRESHOLD) {
            long keep = Math.max(props.getFailureWindow().toMillis(), props.getLockDuration().toMillis());
            for (Iterator<AccountState> it = accounts.values().iterator(); it.hasNext(); ) {
                AccountState s = it.next();
                synchronized (s) {
                    if (s.lockedUntil <= now && now - s.lastTouched > keep) {
                        it.remove();
                    }
                }
            }
        }
        if (ips.size() > PURGE_THRESHOLD) {
            for (Iterator<Deque<Long>> it = ips.values().iterator(); it.hasNext(); ) {
                Deque<Long> q = it.next();
                synchronized (q) {
                    if (q.isEmpty() || now - q.peekLast() >= MINUTE_MILLIS) {
                        it.remove();
                    }
                }
            }
        }
    }

    private static final class AccountState {
        int failures;
        long firstFailureAt;
        long lastTouched;
        long lockedUntil;
    }
}
