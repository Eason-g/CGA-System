package edu.cga;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.cga.service.DatabaseInitializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 集成测试基类：连接 cga_test 库（application-test.yml），每个测试类开始前重建表和种子数据。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class IntegrationTestBase {

    /** 与 application.yml 的 app.demo-password 默认值一致 */
    protected static final String DEMO_PASSWORD = "Cga@2026";

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected JsonMapper json;

    @Autowired
    protected DatabaseInitializer databaseInitializer;

    @BeforeAll
    void rebuildDatabase() {
        databaseInitializer.rebuild();
    }

    /** 用演示密码登录并返回 token */
    protected String login(String account) throws Exception {
        String body = json.writeValueAsString(java.util.Map.of("account", account, "password", DEMO_PASSWORD));
        String resp = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        return json.readTree(resp).path("data").path("token").asString();
    }

    protected static String bearer(String token) {
        return "Bearer " + token;
    }

    /** 每个账号登录一次，token 在测试类内复用 */
    private final java.util.Map<String, String> tokens = new java.util.HashMap<>();

    protected String tokenOf(String account) throws Exception {
        String t = tokens.get(account);
        if (t == null) {
            t = login(account);
            tokens.put(account, t);
        }
        return t;
    }

    /**
     * 以某账号身份调用接口（account 为 null 表示不带 token），断言 HTTP 状态码并返回整个响应体 { code, data, message }。
     */
    protected JsonNode send(org.springframework.http.HttpMethod method, String url, String account, Object body,
                            int expectedStatus) throws Exception {
        var req = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request(method, url);
        if (account != null) {
            req.header("Authorization", bearer(tokenOf(account)));
        }
        if (body != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        var resp = mvc.perform(req).andReturn().getResponse();
        String text = resp.getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        org.assertj.core.api.Assertions.assertThat(resp.getStatus())
                .as("%s %s（%s）→ %s", method, url, account, text)
                .isEqualTo(expectedStatus);
        JsonNode node = json.readTree(text);
        org.assertj.core.api.Assertions.assertThat(node.path("code").asInt()).isEqualTo(expectedStatus);
        return node;
    }

    protected JsonNode apiPost(String url, String account, Object body, int expectedStatus) throws Exception {
        return send(org.springframework.http.HttpMethod.POST, url, account, body, expectedStatus);
    }

    protected JsonNode apiPut(String url, String account, Object body, int expectedStatus) throws Exception {
        return send(org.springframework.http.HttpMethod.PUT, url, account, body, expectedStatus);
    }

    /** 以某账号身份取 snapshot 的 data 节点 */
    protected JsonNode snapshotAs(String account) throws Exception {
        String token = login(account);
        String resp = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/snapshot").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        return json.readTree(resp).path("data");
    }
}
