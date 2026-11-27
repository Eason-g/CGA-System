package edu.cga.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * P2 B 批配置项：校名（app.school-name）、统一身份认证（app.sso.*）、外部系统演示数据（app.integration.*）。
 * 单独成类，避免与 AppProperties 的其他批次改动冲突。
 */
@Data
@ConfigurationProperties(prefix = "app")
public class IntegrationProperties {

    /** 学校名称，由 GET /api/public/site 返回给前端登录页、侧栏、归档页 */
    private String schoolName = "示范高校";

    /** 学校英文名称，英文界面使用（app.school-name-en） */
    private String schoolNameEn = "Demo University";

    private Sso sso = new Sso();
    private Integration integration = new Integration();

    @Data
    public static class Sso {
        /** 是否启用统一身份认证登录；关闭后换票接口返回 404 */
        private boolean enabled = true;
        /** 认证中心实现：mock = 模拟 CAS（MockCasProvider） */
        private String provider = "mock";
        /** 认证中心展示名（登录页按钮下方说明） */
        private String displayName = "统一身份认证（演示环境为模拟 CAS）";
        /** 票据有效期（CAS 约定一次性、短时有效） */
        private Duration ticketTtl = Duration.ofSeconds(60);
        /**
         * 允许回跳的前端地址（只比较 协议+主机+端口）；为空时沿用 app.cors.allowed-origins。
         * 防止模拟认证中心被当作任意跳转的跳板。
         */
        private List<String> allowedServiceOrigins = new ArrayList<>();
        /** 模拟认证中心对外地址前缀；为空表示与本系统同源（开发时由 Vite 把 /mock-cas 转发到后端） */
        private String mockServerUrl = "";
        /** 模拟认证中心的账号目录 */
        private String mockAccounts = "classpath:integration/cas-accounts.json";
    }

    @Data
    public static class Integration {
        /** 模拟学籍系统数据 */
        private String sisData = "classpath:integration/sis-students.json";
        /** 模拟科研系统数据，可配多个文件（后面的同编号项目覆盖前面的） */
        private List<String> researchData = new ArrayList<>(List.of("classpath:integration/research-projects.json"));
    }
}
