package edu.cga.security.login;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import edu.cga.common.BizException;
import org.junit.jupiter.api.Test;

/**
 * P2 D.2 登录防护单元测试（可调时钟）：锁定到期解锁、失败窗口过期重新计数、IP 滑动窗口、并发计数。
 */
class LoginGuardTest {

    /** 可手动拨动的时钟 */
    static final class MutableClock extends Clock {
        long millis = 1_000_000L;

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(millis);
        }

        void advance(Duration d) {
            millis += d.toMillis();
        }
    }

    private final MutableClock clock = new MutableClock();
    private final LoginProtectionProperties props = new LoginProtectionProperties();
    private final LoginGuard guard = new LoginGuard(props, clock);

    private void fail(String key, int times) {
        for (int i = 0; i < times; i++) {
            guard.recordFailure(key);
        }
    }

    @Test
    void lockExpiresAfterLockDuration() {
        fail("U:a", 4);
        assertThatThrownBy(() -> guard.recordFailure("U:a"))
                .isInstanceOfSatisfying(BizException.class, e -> assertThat(e.getCode()).isEqualTo(429));
        clock.advance(Duration.ofMinutes(14));
        assertThatThrownBy(() -> guard.ensureNotLocked("U:a")).hasMessageContaining("1 分钟后再试");
        clock.advance(Duration.ofMinutes(1).plusSeconds(1));
        assertThatCode(() -> guard.ensureNotLocked("U:a")).doesNotThrowAnyException();
        // 解锁后重新从 0 计数
        fail("U:a", 4);
        assertThatCode(() -> guard.ensureNotLocked("U:a")).doesNotThrowAnyException();
    }

    @Test
    void failuresOutsideWindowDoNotAccumulate() {
        fail("U:b", 4);
        clock.advance(Duration.ofMinutes(16));
        fail("U:b", 4);
        assertThatCode(() -> guard.ensureNotLocked("U:b")).doesNotThrowAnyException();
    }

    @Test
    void ipSlidingWindow() {
        props.setIpMaxPerMinute(3);
        assertThat(guard.acquireIp("1.1.1.1")).isZero();
        clock.advance(Duration.ofSeconds(30));
        assertThat(guard.acquireIp("1.1.1.1")).isZero();
        assertThat(guard.acquireIp("1.1.1.1")).isZero();
        assertThat(guard.acquireIp("1.1.1.1")).isBetween(1L, 30L);
        assertThat(guard.acquireIp("2.2.2.2")).isZero();
        clock.advance(Duration.ofSeconds(31)); // 第一条滑出窗口
        assertThat(guard.acquireIp("1.1.1.1")).isZero();
        assertThat(guard.acquireIp("1.1.1.1")).isPositive();
    }

    @Test
    void concurrentFailuresAreCountedSafely() throws Exception {
        props.setMaxFailures(1000);
        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger locked = new AtomicInteger();
        for (int i = 0; i < 999; i++) {
            pool.submit(() -> {
                go.await();
                guard.recordFailure("U:c");
                return null;
            });
        }
        go.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        // 第 1000 次恰好触发锁定：说明并发下 999 次一次不丢
        try {
            guard.recordFailure("U:c");
        } catch (BizException e) {
            locked.incrementAndGet();
        }
        assertThat(locked.get()).isEqualTo(1);
    }
}
