package edu.cga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import edu.cga.crypto.SensitiveDataMigrator;
import edu.cga.security.login.LoginGuard;
import edu.cga.security.login.LoginProtectionProperties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.JsonNode;

/**
 * P2 D.2 登录防护与安全头：连续失败锁定、锁定期正确密码也 429、成功清零、换着标识试也算同一账号、
 * 不存在的账号同样锁定（不泄露账号是否存在）、IP 限流、安全响应头。
 * 计数在共享的 Spring 上下文里，所以只用本类新建的账号，结束时清空计数。
 */
class LoginProtectionIntegrationTest extends WriteApiTestBase {

    @Autowired
    private LoginGuard guard;
    @Autowired
    private LoginProtectionProperties props;
    @Autowired
    private SensitiveDataMigrator migrator;

    private int seq;

    @BeforeEach
    void clearCounters() {
        guard.reset();
    }

    @AfterAll
    void cleanup() {
        guard.reset();
    }

    private String newStudent() {
        String id = "T_LOCK_" + (++seq);
        createStudent(id, "DEPT_CS", false, true);
        migrator.migrate(); // 补邮箱检索哈希
        return id.toLowerCase();
    }

    private MockHttpServletResponse login(String account, String password, String ip) throws Exception {
        String body = json.writeValueAsString(Map.of("account", account, "password", password));
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body)
                .with(r -> {
                    r.setRemoteAddr(ip);
                    return r;
                })).andReturn().getResponse();
    }

    private MockHttpServletResponse login(String account, String password) throws Exception {
        return login(account, password, "127.0.0.1");
    }

    private JsonNode body(MockHttpServletResponse resp) throws Exception {
        return json.readTree(resp.getContentAsString(StandardCharsets.UTF_8));
    }

    @Test
    void mockCasAndPasswordShareTheSameLocalAccountLock() throws Exception {
        String user = "alex.chen";
        for (int attempt = 0; attempt < 5; attempt++) {
            assertThat(login(user, "incorrect").getStatus()).isEqualTo(attempt == 4 ? 429 : 401);
        }
        MockHttpServletResponse casResponse = mvc.perform(post("/mock-cas/login")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("uid", "2025210891").param("password", DEMO_PASSWORD)
                .param("service", "http://localhost:5173/")).andReturn().getResponse();
        assertThat(casResponse.getStatus()).isEqualTo(429);
    }

    @Test
    void fiveFailuresLockAccountAndCorrectPasswordGets429() throws Exception {
        String user = newStudent();
        for (int i = 1; i <= 4; i++) {
            MockHttpServletResponse r = login(user, "wrong-" + i);
            assertThat(r.getStatus()).isEqualTo(401);
            assertThat(body(r).path("message").asString()).isEqualTo("账号或密码错误");
        }
        MockHttpServletResponse fifth = login(user, "wrong-5");
        assertThat(fifth.getStatus()).isEqualTo(429);
        assertThat(body(fifth).path("message").asString()).contains("锁定").contains("15 分钟");

        MockHttpServletResponse correct = login(user, DEMO_PASSWORD);
        assertThat(correct.getStatus()).isEqualTo(429);
        JsonNode b = body(correct);
        assertThat(b.path("code").asInt()).isEqualTo(429);
        assertThat(b.path("message").asString()).contains("分钟后再试");
        assertThat(b.path("data").path("retryAfterSeconds").asLong()).isBetween(1L, 900L);
        assertThat(b.path("data").has("token")).isFalse();
    }

    @Test
    void successfulLoginResetsFailureCount() throws Exception {
        String user = newStudent();
        for (int i = 0; i < 4; i++) {
            assertThat(login(user, "wrong").getStatus()).isEqualTo(401);
        }
        assertThat(login(user, DEMO_PASSWORD).getStatus()).isEqualTo(200);
        for (int i = 0; i < 4; i++) {
            assertThat(login(user, "wrong").getStatus()).isEqualTo(401);
        }
        assertThat(login(user, DEMO_PASSWORD).getStatus()).isEqualTo(200);
    }

    @Test
    void alternatingUsernameAndEmailCountsAsSameAccount() throws Exception {
        String user = newStudent();
        String email = user + "@univ.edu.cn";
        for (int i = 0; i < 2; i++) {
            assertThat(login(user, "wrong").getStatus()).isEqualTo(401);
            assertThat(login(email.toUpperCase(), "wrong").getStatus()).isEqualTo(401);
        }
        assertThat(login(email, "wrong").getStatus()).isEqualTo(429);
        assertThat(login(user, DEMO_PASSWORD).getStatus()).isEqualTo(429);
        // 其他账号不受影响
        assertThat(login(newStudent(), DEMO_PASSWORD).getStatus()).isEqualTo(200);
    }

    @Test
    void unknownAccountIsLockedTooSoExistenceIsNotRevealed() throws Exception {
        for (int i = 0; i < 4; i++) {
            assertThat(login("no.such.person", "x").getStatus()).isEqualTo(401);
        }
        assertThat(login("no.such.person", "x").getStatus()).isEqualTo(429);
    }

    @Test
    void ipRateLimitRejectsExcessLoginRequests() throws Exception {
        int original = props.getIpMaxPerMinute();
        props.setIpMaxPerMinute(3);
        try {
            String user = newStudent();
            assertThat(login(user, DEMO_PASSWORD, "10.20.30.40").getStatus()).isEqualTo(200);
            assertThat(login(user, "wrong", "10.20.30.40").getStatus()).isEqualTo(401);
            assertThat(login(user, DEMO_PASSWORD, "10.20.30.40").getStatus()).isEqualTo(200);
            MockHttpServletResponse limited = login(user, DEMO_PASSWORD, "10.20.30.40");
            assertThat(limited.getStatus()).isEqualTo(429);
            assertThat(limited.getHeader("Retry-After")).isNotBlank();
            assertThat(body(limited).path("message").asString()).contains("过于频繁");
            // 换一个 IP 不受影响；非登录接口不计数
            assertThat(login(user, DEMO_PASSWORD, "10.20.30.41").getStatus()).isEqualTo(200);
            assertThat(mvc.perform(get("/api/snapshot").with(r -> {
                r.setRemoteAddr("10.20.30.40");
                return r;
            })).andReturn().getResponse().getStatus()).isEqualTo(401);
        } finally {
            props.setIpMaxPerMinute(original);
        }
    }

    @Test
    void securityHeadersArePresent() throws Exception {
        MockHttpServletResponse api = mvc.perform(get("/api/snapshot")).andReturn().getResponse();
        assertThat(api.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(api.getHeader("X-Frame-Options")).isEqualTo("DENY");
        assertThat(api.getHeader("Referrer-Policy")).isEqualTo("strict-origin-when-cross-origin");
        assertThat(api.getHeader("Content-Security-Policy"))
                .contains("default-src 'self'").contains("frame-ancestors 'none'").doesNotContain("unsafe-inline");

        MockHttpServletResponse loginResp = login("lin.zhiyuan", DEMO_PASSWORD);
        assertThat(loginResp.getStatus()).isEqualTo(200);
        assertThat(loginResp.getHeader("X-Frame-Options")).isEqualTo("DENY");
        assertThat(loginResp.getHeader("Content-Security-Policy")).isNotBlank();

        // 统一认证模拟页（B 批）：允许内联脚本样式以便直接打开，但同样禁止被嵌入
        MockHttpServletResponse cas = mvc.perform(get("/mock-cas/login")).andReturn().getResponse();
        assertThat(cas.getHeader("Content-Security-Policy")).contains("'unsafe-inline'").contains("frame-ancestors 'none'");
        assertThat(cas.getHeader("X-Frame-Options")).isEqualTo("DENY");
    }
}
