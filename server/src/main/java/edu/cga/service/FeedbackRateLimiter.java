package edu.cga.service;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import edu.cga.common.BizException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 公开评价接口的进程内限流：固定时间窗口计数（单实例部署够用，多实例需换成 Redis 等共享存储）。
 * <p>
 * 阈值按“一个班几十人连同一个校园网出口（同一 IP）在几分钟内集中评价”设定，默认每 10 分钟：
 * <ul>
 *   <li>同一 IP + 同一评价码：查询 300 次、提交 150 次（60 人的班每人查 2–3 次、提交 1–2 次仍有富余）；</li>
 *   <li>同一 IP 不分评价码的查询总数：600 次（拦住逐个猜评价码，又不影响同一出口下几门课同时评价）。</li>
 * </ul>
 * 超限返回 429。IP 取 {@code request.getRemoteAddr()}，不信任可伪造的 X-Forwarded-For；
 * 部署在反向代理后面时应由代理配置真实客户端地址。
 */
@Component
public class FeedbackRateLimiter {

    private final long windowMillis;
    private final int queryLimit;
    private final int submitLimit;
    private final int ipQueryLimit;

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public FeedbackRateLimiter(@Value("${app.feedback.rate-limit.window-seconds:600}") long windowSeconds,
                               @Value("${app.feedback.rate-limit.query-per-code:300}") int queryLimit,
                               @Value("${app.feedback.rate-limit.submit-per-code:150}") int submitLimit,
                               @Value("${app.feedback.rate-limit.query-per-ip:600}") int ipQueryLimit) {
        this.windowMillis = windowSeconds * 1000L;
        this.queryLimit = queryLimit;
        this.submitLimit = submitLimit;
        this.ipQueryLimit = ipQueryLimit;
    }

    public int queryLimit() {
        return queryLimit;
    }

    public int submitLimit() {
        return submitLimit;
    }

    /** 查询评价码：同时计入“IP + 评价码”和“IP 总量”两个窗口 */
    public void checkQuery(String ip, String code) {
        hit("Q|" + ip + "|" + code, queryLimit);
        hit("QIP|" + ip, ipQueryLimit);
    }

    /** 提交评价 */
    public void checkSubmit(String ip, String code) {
        hit("S|" + ip + "|" + code, submitLimit);
    }

    private void hit(String key, int limit) {
        long now = System.currentTimeMillis();
        if (windows.size() > 50_000) {
            purge(now);
        }
        Window w = windows.compute(key, (k, old) -> old == null || now - old.start >= windowMillis ? new Window(now) : old);
        if (w.count.incrementAndGet() > limit) {
            throw new BizException(429, "操作过于频繁，请稍后再试");
        }
    }

    /** 清理已过期的窗口，避免长期运行后计数表无限增长 */
    private void purge(long now) {
        for (Iterator<Map.Entry<String, Window>> it = windows.entrySet().iterator(); it.hasNext(); ) {
            if (now - it.next().getValue().start >= windowMillis) {
                it.remove();
            }
        }
    }

    private static final class Window {
        final long start;
        final AtomicInteger count = new AtomicInteger();

        Window(long start) {
            this.start = start;
        }
    }
}
