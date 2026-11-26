package edu.cga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * 设计文档第 9 节第 1 条：登录成功、密码错误 401、无 token 访问 401、学生调管理接口 403。
 */
class AuthIntegrationTest extends IntegrationTestBase {

    private String loginBody(String account, String password) {
        return json.writeValueAsString(Map.of("account", account, "password", password));
    }

    @Test
    void loginByUsernameReturnsTokenAndUserWithoutPasswordHash() throws Exception {
        String resp = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("lin.zhiyuan", DEMO_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.user.id").value("U_CN_STU"))
                .andExpect(jsonPath("$.data.user.role").value("STUDENT"))
                .andExpect(jsonPath("$.data.user.passwordHash").doesNotExist())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(resp).doesNotContain("passwordHash").doesNotContain("password_hash");
    }

    @Test
    void loginByStudentNoAndEmailIgnoringCase() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("2025210891", DEMO_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.id").value("U_INTL_STU"));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("ZHOU.GQ@univ.edu.cn", DEMO_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.id").value("U_SCHOOL_ADMIN"));
    }

    @Test
    void wrongPasswordOrUnknownAccountReturns401WithSameMessage() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("lin.zhiyuan", "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("账号或密码错误"));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("no.such.user", DEMO_PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("账号或密码错误"));
    }

    @Test
    void blankAccountReturns400() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void requestWithoutTokenReturns401Json() throws Exception {
        mvc.perform(get("/api/snapshot"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("未登录或登录已失效"));
    }

    @Test
    void tamperedTokenReturns401() throws Exception {
        String token = login("lin.zhiyuan");
        mvc.perform(get("/api/auth/me").header("Authorization", bearer(token + "x")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void meReturnsCurrentUser() throws Exception {
        String token = login("alex.chen");
        mvc.perform(get("/api/auth/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("U_INTL_STU"))
                .andExpect(jsonPath("$.data.phone").value("138-0010-2091"))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
    }

    @Test
    void studentCallingAdminApiReturns403() throws Exception {
        String token = login("lin.zhiyuan");
        mvc.perform(post("/api/demo/reset").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void schoolAdminCanResetDemoData() throws Exception {
        String token = login("zhou.guoqiang");
        mvc.perform(post("/api/demo/reset").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("已重置为初始演示数据"))
                .andExpect(jsonPath("$.data.jobs.length()").value(11));
        // 重置后演示密码依然可用
        assertThat(login("lin.zhiyuan")).isNotBlank();
    }
}
