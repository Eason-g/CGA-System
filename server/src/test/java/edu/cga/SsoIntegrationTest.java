package edu.cga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import edu.cga.config.IntegrationProperties;
import edu.cga.domain.entity.AuditLog;
import edu.cga.integration.sso.MockCasProvider;
import edu.cga.integration.sso.SsoIdentity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.JsonNode;

/**
 * P2 B.1 统一身份认证（模拟 CAS）与 B.4 站点信息：正常换票、票据过期/重复使用/service 不一致 401、
 * 未开通账号 403、SSO 关闭 404、模拟认证中心页面与回跳、/api/public/site 返回校名。
 */
class SsoIntegrationTest extends WriteApiTestBase {

    /** 必须在 app.cors.allowed-origins 之内（application.yml 默认含 5173） */
    private static final String SERVICE = "http://localhost:5173/";

    @Autowired
    private MockCasProvider cas;
    @Autowired
    private IntegrationProperties props;

    private JsonNode exchange(String ticket, String service, int expected) throws Exception {
        return apiPost("/api/auth/sso/exchange", null, Map.of("ticket", ticket, "service", service), expected);
    }

    /** 在模拟认证中心登录，返回 302 的 Location */
    private MockHttpServletResponse casLogin(String uid, String password, String service) throws Exception {
        return mvc.perform(post("/mock-cas/login").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("uid", uid).param("password", password).param("service", service))
                .andReturn().getResponse();
    }

    private static String ticketOf(String location) {
        String query = URI.create(location).getRawQuery();
        assertThat(query).startsWith("ticket=ST-");
        return query.substring("ticket=".length());
    }

    @Test
    void fullCasFlowIssuesJwtAndWritesAudit() throws Exception {
        // 认证中心页面（免登录）
        MockHttpServletResponse page = mvc.perform(get("/mock-cas/login").param("service", SERVICE))
                .andReturn().getResponse();
        assertThat(page.getStatus()).isEqualTo(200);
        assertThat(page.getContentAsString(StandardCharsets.UTF_8)).contains("模拟统一身份认证中心（演示）");

        // 登录名接受学号，大小写不敏感的用户名也可以
        MockHttpServletResponse login = casLogin("2025210891", DEMO_PASSWORD, SERVICE);
        assertThat(login.getStatus()).isEqualTo(302);
        String location = login.getHeader("Location");
        assertThat(location).startsWith(SERVICE + "?ticket=ST-");

        long ssoLogs = auditLogMapper.selectCount(new QueryWrapper<AuditLog>().eq("action", "统一身份认证登录"));
        JsonNode r = exchange(ticketOf(location), SERVICE, 200);
        assertThat(r.path("data").path("user").path("id").asString()).isEqualTo("U_INTL_STU");
        assertThat(r.path("data").path("user").has("passwordHash")).isFalse();
        String token = r.path("data").path("token").asString();
        String me = mvc.perform(get("/api/auth/me").header("Authorization", bearer(token)))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(json.readTree(me).path("data").path("username").asString()).isEqualTo("alex.chen");
        assertThat(auditLogMapper.selectCount(new QueryWrapper<AuditLog>().eq("action", "统一身份认证登录")))
                .isEqualTo(ssoLogs + 1);

        // 同一张票据第二次使用 → 401
        JsonNode reused = exchange(ticketOf(location), SERVICE, 401);
        assertThat(reused.path("message").asString()).isEqualTo(MockCasProvider.TICKET_INVALID);
    }

    @Test
    void staffCanLoginWithUsername() throws Exception {
        String location = casLogin("ZHANG.MINGYUAN", DEMO_PASSWORD, SERVICE).getHeader("Location");
        JsonNode r = exchange(ticketOf(location), SERVICE, 200);
        assertThat(r.path("data").path("user").path("role").asString()).isEqualTo("MENTOR");
    }

    @Test
    void expiredTicketIs401() throws Exception {
        String ticket = cas.issueTicket(new SsoIdentity("2023011420", "林致远"), SERVICE,
                Instant.now().minusSeconds(61));
        JsonNode r = exchange(ticket, SERVICE, 401);
        assertThat(r.path("message").asString()).isEqualTo(MockCasProvider.TICKET_EXPIRED);
    }

    @Test
    void serviceMismatchIs401AndConsumesTicket() throws Exception {
        String ticket = cas.issueTicket(new SsoIdentity("2023011420", "林致远"), SERVICE);
        JsonNode r = exchange(ticket, "http://localhost:5174/", 401);
        assertThat(r.path("message").asString()).isEqualTo(MockCasProvider.SERVICE_MISMATCH);
        // 校验失败后票据同样作废
        exchange(ticket, SERVICE, 401);
        exchange("ST-forged", SERVICE, 401);
    }

    @Test
    void accountNotOpenedIs403() throws Exception {
        String location = casLogin("2026990001", DEMO_PASSWORD, SERVICE).getHeader("Location");
        JsonNode r = exchange(ticketOf(location), SERVICE, 403);
        assertThat(r.path("message").asString()).isEqualTo("统一认证账号未在本系统开通");
    }

    @Test
    void casLoginFormLocksAccountAfterRepeatedFailures() throws Exception {
        // 认证中心表单与本系统密码登录同样防暴力破解：连续失败达到阈值后锁定，锁定期内正确密码也被拒（429）
        String uid = "2025220418";
        int last = 0;
        for (int i = 0; i < 5; i++) {
            last = casLogin(uid, "wrong-password-" + i, SERVICE).getStatus();
        }
        assertThat(last).isEqualTo(429);
        MockHttpServletResponse locked = casLogin(uid, DEMO_PASSWORD, SERVICE);
        assertThat(locked.getStatus()).isEqualTo(429);
        assertThat(locked.getContentAsString(StandardCharsets.UTF_8)).doesNotContain("ticket=");
    }

    @Test
    void casRejectsWrongPasswordAndForeignService() throws Exception {
        MockHttpServletResponse bad = casLogin("2025210891", "wrong-password", SERVICE);
        assertThat(bad.getStatus()).isEqualTo(401);
        assertThat(bad.getContentAsString(StandardCharsets.UTF_8)).contains("学工号或密码错误");
        // 不在允许范围内的回跳地址（防开放跳转）
        assertThat(mvc.perform(get("/mock-cas/login").param("service", "https://evil.example.com/"))
                .andReturn().getResponse().getStatus()).isEqualTo(400);
        assertThat(casLogin("2025210891", DEMO_PASSWORD, "https://evil.example.com/").getStatus()).isEqualTo(400);
    }

    @Test
    void loginUrlAndTicketAppendKeepHashRoute() throws Exception {
        JsonNode r = send(HttpMethod.GET, "/api/auth/sso/login-url?service=" + SERVICE, null, null, 200);
        assertThat(r.path("data").path("loginUrl").asString())
                .isEqualTo("/mock-cas/login?service=http%3A%2F%2Flocalhost%3A5173%2F");
        assertThat(MockCasProvider.appendTicket("http://localhost:5173/#/login", "ST-1"))
                .isEqualTo("http://localhost:5173/?ticket=ST-1#/login");
        assertThat(MockCasProvider.appendTicket("http://localhost:5173/?a=1", "ST-1"))
                .isEqualTo("http://localhost:5173/?a=1&ticket=ST-1");
    }

    @Test
    void ssoDisabledReturns404() throws Exception {
        String ticket = cas.issueTicket(new SsoIdentity("2023011420", "林致远"), SERVICE);
        props.getSso().setEnabled(false);
        try {
            exchange(ticket, SERVICE, 404);
            send(HttpMethod.GET, "/api/auth/sso/login-url?service=" + SERVICE, null, null, 404);
            assertThat(mvc.perform(get("/mock-cas/login").param("service", SERVICE))
                    .andReturn().getResponse().getStatus()).isEqualTo(404);
            JsonNode site = send(HttpMethod.GET, "/api/public/site", null, null, 200);
            assertThat(site.path("data").path("sso").path("enabled").asBoolean()).isFalse();
        } finally {
            props.getSso().setEnabled(true);
        }
    }

    @Test
    void publicSiteReturnsSchoolName() throws Exception {
        JsonNode r = send(HttpMethod.GET, "/api/public/site", null, null, 200);
        assertThat(r.path("data").path("schoolName").asString()).isEqualTo("示范高校");
        assertThat(r.path("data").path("sso").path("enabled").asBoolean()).isTrue();
        assertThat(r.path("data").path("sso").path("displayName").asString()).contains("模拟 CAS");
    }
}
