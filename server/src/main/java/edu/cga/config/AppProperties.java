package edu.cga.config;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * application.yml 中 app.* 配置项。默认值写在 application.yml 里，这里只负责绑定。
 */
@Data
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    /** 演示日期；为空时使用系统日期 */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate today;

    /** 学期第一天，用于计算当前教学周次 */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate semesterStart;

    /** 种子用户的演示密码 */
    private String demoPassword;

    private Jwt jwt = new Jwt();
    private Db db = new Db();
    private Demo demo = new Demo();
    private Cors cors = new Cors();

    @Data
    public static class Jwt {
        /** HS256 密钥，至少 32 字节 */
        private String secret;
        /** token 有效期 */
        private Duration ttl = Duration.ofHours(8);
    }

    @Data
    public static class Db {
        private InitMode init = InitMode.IF_EMPTY;
    }

    @Data
    public static class Demo {
        private boolean resetEnabled;
    }

    @Data
    public static class Cors {
        private List<String> allowedOrigins = new ArrayList<>();
    }

    /** 数据库初始化方式 */
    public enum InitMode {
        /** users 表不存在时建表并导入种子数据 */
        IF_EMPTY,
        /** 每次启动都重建 */
        ALWAYS,
        /** 不做任何处理 */
        NEVER
    }
}
