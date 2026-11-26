package edu.cga.security.login;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 登录防护配置（P2 D.2），对应 application.yml 的 app.security.login.*。
 */
@Data
@ConfigurationProperties(prefix = "app.security.login")
public class LoginProtectionProperties {

    /** 统计连续失败的时间窗口 */
    private Duration failureWindow = Duration.ofMinutes(15);

    /** 窗口内连续失败多少次锁定账号 */
    private int maxFailures = 5;

    /** 锁定时长 */
    private Duration lockDuration = Duration.ofMinutes(15);

    /**
     * 同一 IP 每分钟登录请求上限（成功失败都算）。
     * 按“一个机房 / 一个宿舍楼共用一个校园网出口”估算：上课时 100 人左右在一两分钟内集中登录，
     * 每人输错一两次也不超过 300；而在线猜密码通常每分钟成千上万次，300 足以把速度压到无意义。
     */
    private int ipMaxPerMinute = 300;

    /**
     * 计入 IP 限流的接口路径（POST），默认含密码登录和统一认证换票（P2 B 批）；
     * 其他登录入口把路径加到这里即可，不用改代码，也可以在代码里直接调用 {@link LoginGuard#checkIp(String)}。
     */
    private List<String> ipLimitedPaths = new ArrayList<>(List.of("/api/auth/login", "/api/auth/sso/exchange"));
}
