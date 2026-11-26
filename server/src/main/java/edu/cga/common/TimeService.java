package edu.cga.common;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

import edu.cga.config.AppProperties;
import org.springframework.stereotype.Service;

/**
 * 时间服务：业务代码一律通过它取“今天/现在/当前周次/当前月份”，不得写死日期。
 * <ul>
 *   <li>配置了 app.today 时，“今天”固定为该日期（时分秒仍取时钟），便于演示；</li>
 *   <li>当前周次 = (今天 − 学期第一天) 的天数 / 7 + 1，学期开始前按第 1 周算；</li>
 *   <li>当前月份 = 今天的 yyyy-MM。</li>
 * </ul>
 */
@Service
public class TimeService {

    public static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    public static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final Clock clock;
    private final AppProperties props;

    public TimeService(Clock clock, AppProperties props) {
        this.clock = clock;
        this.props = props;
    }

    public LocalDate today() {
        return props.getToday() != null ? props.getToday() : LocalDate.now(clock);
    }

    public LocalDateTime now() {
        return LocalDateTime.of(today(), LocalTime.now(clock).withNano(0));
    }

    /** 与前端 nowTimestamp() 相同格式：yyyy-MM-dd HH:mm:ss */
    public String nowTimestamp() {
        return now().format(TIMESTAMP_FORMAT);
    }

    public int currentWeekNo() {
        return weekNoOf(today());
    }

    public int weekNoOf(LocalDate date) {
        LocalDate start = props.getSemesterStart();
        if (start == null) {
            throw new IllegalStateException("未配置 app.semester-start");
        }
        long days = ChronoUnit.DAYS.between(start, date);
        if (days < 0) {
            return 1;
        }
        return (int) (days / 7) + 1;
    }

    public String currentMonth() {
        return today().format(MONTH_FORMAT);
    }
}
