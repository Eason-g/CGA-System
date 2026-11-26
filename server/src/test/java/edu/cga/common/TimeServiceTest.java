package edu.cga.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import edu.cga.config.AppProperties;
import org.junit.jupiter.api.Test;

/**
 * TimeService 单元测试（不连数据库）。
 */
class TimeServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private static AppProperties props(LocalDate today) {
        AppProperties p = new AppProperties();
        p.setToday(today);
        p.setSemesterStart(LocalDate.parse("2026-08-31"));
        return p;
    }

    @Test
    void seedDateIsWeek12OfNovember() {
        TimeService t = new TimeService(Clock.system(ZONE), props(LocalDate.parse("2026-11-20")));
        assertThat(t.today()).isEqualTo(LocalDate.parse("2026-11-20"));
        assertThat(t.currentWeekNo()).isEqualTo(12);
        assertThat(t.currentMonth()).isEqualTo("2026-11");
        assertThat(t.nowTimestamp()).startsWith("2026-11-20 ");
    }

    @Test
    void usesClockWhenTodayNotConfigured() {
        Clock fixed = Clock.fixed(Instant.parse("2026-09-07T02:00:00Z"), ZONE); // 北京时间 2026-09-07 10:00
        TimeService t = new TimeService(fixed, props(null));
        assertThat(t.today()).isEqualTo(LocalDate.parse("2026-09-07"));
        assertThat(t.currentWeekNo()).isEqualTo(2);
        assertThat(t.currentMonth()).isEqualTo("2026-09");
        assertThat(t.nowTimestamp()).isEqualTo("2026-09-07 10:00:00");
    }

    @Test
    void weekBoundaries() {
        TimeService t = new TimeService(Clock.system(ZONE), props(null));
        assertThat(t.weekNoOf(LocalDate.parse("2026-08-31"))).isEqualTo(1);
        assertThat(t.weekNoOf(LocalDate.parse("2026-09-06"))).isEqualTo(1);
        assertThat(t.weekNoOf(LocalDate.parse("2026-09-07"))).isEqualTo(2);
        assertThat(t.weekNoOf(LocalDate.parse("2026-08-01"))).isEqualTo(1);
    }
}
