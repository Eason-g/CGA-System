package edu.cga.config;

import java.time.Duration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * P1 第三批配置项（app.upload-dir、app.finance.*）。单独成类，避免与 AppProperties 的其他批次改动冲突。
 */
@Data
@ConfigurationProperties(prefix = "app")
public class MaterialFinanceProperties {

    /** 材料上传存储目录（相对路径相对于启动目录），默认 uploads（即 server/uploads/） */
    private String uploadDir = "uploads";

    private Finance finance = new Finance();

    @Data
    public static class Finance {
        /** 财务回调 HMAC-SHA256 签名密钥；生产环境必须通过环境变量 APP_FINANCE_CALLBACK_SECRET 覆盖 */
        private String callbackSecret;
        private Mock mock = new Mock();
    }

    /** 模拟财务系统（MockFinanceGateway） */
    @Data
    public static class Mock {
        /** 提交后是否由定时任务自动发起回调（测试中关闭） */
        private boolean autoCallback = true;
        /** 自动回调的延迟 */
        private Duration callbackDelay = Duration.ofSeconds(3);
        /** 自动回调的成功率（0–1，默认 1 即 100%） */
        private double successRate = 1.0;
    }
}
