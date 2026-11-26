package edu.cga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import edu.cga.finance.FinanceSignature;
import edu.cga.service.AttachmentService;
import org.junit.jupiter.api.AfterAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import tools.jackson.databind.JsonNode;

/**
 * P1 第三批测试基类：上传/下载、带签名的财务回调；测试类结束后清理上传目录（application-test.yml 指向系统临时目录）。
 */
public abstract class MaterialFinanceTestBase extends WriteApiTestBase {

    /** 与 application-test.yml 的 app.finance.callback-secret 一致 */
    protected static final String CALLBACK_SECRET = "test-finance-callback-secret";

    /** 最小的合法 PDF / PNG 文件头 */
    protected static final byte[] PDF_BYTES = "%PDF-1.4\n%测试文件\n1 0 obj<<>>endobj\ntrailer<<>>\n%%EOF\n"
            .getBytes(StandardCharsets.UTF_8);
    protected static final byte[] PNG_BYTES = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D,
            'I', 'H', 'D', 'R', 0, 0, 0, 1, 0, 0, 0, 1, 8, 2, 0, 0, 0};

    @Autowired
    protected AttachmentService attachmentService;

    @AfterAll
    void cleanUploadDir() throws IOException {
        Path dir = attachmentService.uploadDir();
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // 尽力清理
                }
            });
        }
    }

    /** 以某账号上传文件，断言状态码并返回响应体 */
    protected JsonNode upload(String account, String fileName, byte[] content, String kind, int expectedStatus)
            throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", fileName, "application/octet-stream", content);
        var req = multipart("/api/attachments").file(file).param("kind", kind);
        if (account != null) {
            req.header("Authorization", bearer(tokenOf(account)));
        }
        MockHttpServletResponse resp = mvc.perform(req).andReturn().getResponse();
        String text = resp.getContentAsString(StandardCharsets.UTF_8);
        assertThat(resp.getStatus()).as("上传 %s（%s）→ %s", fileName, account, text).isEqualTo(expectedStatus);
        JsonNode node = json.readTree(text);
        assertThat(node.path("code").asInt()).isEqualTo(expectedStatus);
        return node;
    }

    /** 以某账号下载附件，返回原始响应 */
    protected MockHttpServletResponse download(String account, String attachmentId) throws Exception {
        var req = get("/api/attachments/" + attachmentId);
        if (account != null) {
            req.header("Authorization", bearer(tokenOf(account)));
        }
        return mvc.perform(req).andReturn().getResponse();
    }

    /** 以财务系统身份发起回调（不带登录令牌），signature 为 null 时按 CALLBACK_SECRET 正确签名 */
    protected JsonNode financeCallback(String rawBody, String signature, int expectedStatus) throws Exception {
        String sig = signature != null ? signature : FinanceSignature.sign(CALLBACK_SECRET, rawBody);
        MockHttpServletResponse resp = mvc.perform(post("/api/integration/finance/callback")
                        .contentType(MediaType.APPLICATION_JSON).content(rawBody)
                        .header(FinanceSignature.HEADER, sig))
                .andReturn().getResponse();
        String text = resp.getContentAsString(StandardCharsets.UTF_8);
        assertThat(resp.getStatus()).as("财务回调 → %s", text).isEqualTo(expectedStatus);
        return json.readTree(text);
    }

    protected String callbackBody(String serialNo, boolean success, String voucherNo, String reason) {
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("serialNo", serialNo);
        m.put("success", success);
        m.put("voucherNo", voucherNo);
        m.put("reason", reason);
        m.put("timestamp", "2026-11-20 10:00:00");
        return json.writeValueAsString(m);
    }
}
