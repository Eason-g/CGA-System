package edu.cga;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.util.Arrays;
import java.util.Map;

import edu.cga.domain.entity.Attachment;
import edu.cga.domain.mapper.AttachmentMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.JsonNode;

/**
 * P1 第三批 3.1 材料上传：类型识别（魔数）、大小上限、下载权限、申请关联附件。
 */
class AttachmentIntegrationTest extends MaterialFinanceTestBase {

    @Autowired
    private AttachmentMapper attachmentMapper;

    @Test
    void validPdfAndPngAreStored() throws Exception {
        JsonNode pdf = upload("alex.chen", "个人简历.pdf", PDF_BYTES, "RESUME", 200);
        JsonNode d = pdf.path("data");
        assertThat(d.path("contentType").asString()).isEqualTo("application/pdf");
        assertThat(d.path("originalName").asString()).isEqualTo("个人简历.pdf");
        assertThat(d.path("sizeBytes").asLong()).isEqualTo(PDF_BYTES.length);
        assertThat(d.path("sha256").asString()).hasSize(64);
        assertThat(d.path("ownerId").asString()).isEqualTo("U_INTL_STU");
        assertThat(d.has("storedName")).isFalse();
        Attachment row = attachmentMapper.selectById(d.path("id").asString());
        assertThat(Files.exists(attachmentService.uploadDir().resolve(row.getStoredName()))).isTrue();
        // 磁盘文件名是随机 ID，不含原文件名
        assertThat(row.getStoredName()).doesNotContain("简历").endsWith(".pdf");

        JsonNode png = upload("alex.chen", "photo.PNG", PNG_BYTES, "OTHER", 200);
        assertThat(png.path("data").path("contentType").asString()).isEqualTo("image/png");
    }

    @Test
    void forgedExtensionIsRejected() throws Exception {
        long before = attachmentMapper.selectCount(null);
        // 文本内容改名为 .pdf
        JsonNode r1 = upload("alex.chen", "resume.pdf", "这不是PDF".getBytes(), "RESUME", 400);
        assertThat(r1.path("message").asString()).contains("只支持 PDF、JPG、PNG");
        // 可执行文件头改名为 .png
        upload("alex.chen", "a.png", new byte[]{'M', 'Z', (byte) 0x90, 0, 3, 0, 0, 0}, "OTHER", 400);
        // 真实 PNG 却冒充 .pdf：扩展名与内容不符同样拒收
        JsonNode r3 = upload("alex.chen", "fake.pdf", PNG_BYTES, "RESUME", 400);
        assertThat(r3.path("message").asString()).contains("扩展名与实际内容不符");
        // 非法 kind
        upload("alex.chen", "a.pdf", PDF_BYTES, "PASSPORT", 400);
        assertThat(attachmentMapper.selectCount(null)).isEqualTo(before);
    }

    @Test
    void fileOver10MbIsRejectedWithJson() throws Exception {
        byte[] big = new byte[10 * 1024 * 1024 + 1];
        Arrays.fill(big, (byte) ' ');
        System.arraycopy(PDF_BYTES, 0, big, 0, PDF_BYTES.length);
        JsonNode r = upload("alex.chen", "big.pdf", big, "OTHER", 400);
        assertThat(r.path("message").asString()).contains("10MB");
    }

    @Test
    void onlyStudentsCanUploadAndLoginRequired() throws Exception {
        upload("zhang.mingyuan", "a.pdf", PDF_BYTES, "OTHER", 403);
        upload(null, "a.pdf", PDF_BYTES, "OTHER", 401);
    }

    @Test
    void downloadPermissions() throws Exception {
        // Alex 向张明远导师的 JOB_10、JOB_11 投递过申请；与数学学院导师无关
        String id = upload("alex.chen", "visa.pdf", PDF_BYTES, "VISA", 200).path("data").path("id").asString();

        MockHttpServletResponse own = download("alex.chen", id);
        assertThat(own.getStatus()).isEqualTo(200);
        assertThat(own.getContentAsByteArray()).isEqualTo(PDF_BYTES);
        assertThat(own.getContentType()).startsWith("application/pdf");
        assertThat(own.getHeader("Content-Disposition")).startsWith("attachment").contains("filename*=");

        assertThat(download("zhang.mingyuan", id).getStatus()).isEqualTo(200); // 所投岗位导师
        assertThat(download("wang.haitao", id).getStatus()).isEqualTo(200);    // 国际生办（国际生材料）
        assertThat(download("li.wanqing", id).getStatus()).isEqualTo(200);     // 本院系管理员
        assertThat(download("zhou.guoqiang", id).getStatus()).isEqualTo(200);  // 校级管理员
        assertThat(download("chen.shouren", id).getStatus()).isEqualTo(404);   // 外院导师
        assertThat(download("zhao.minghua", id).getStatus()).isEqualTo(404);
        assertThat(download("lin.zhiyuan", id).getStatus()).isEqualTo(404);    // 其他学生
        assertThat(download(null, id).getStatus()).isEqualTo(401);
        assertThat(download("alex.chen", "ATT_NOPE00").getStatus()).isEqualTo(404);

        // 国际生办看不到国内生的材料
        String cn = upload("lin.zhiyuan", "cv.pdf", PDF_BYTES, "RESUME", 200).path("data").path("id").asString();
        assertThat(download("wang.haitao", cn).getStatus()).isEqualTo(404);
    }

    @Test
    void applicationKeepsAttachmentIds() throws Exception {
        createStudent("T_ATT_STU", "DEPT_MATH", false, true);
        createPublishedJob("T_ATT_J1", "U_MENTOR", "DEPT_CS", "B_CS_RA", 6, false);
        String resume = upload("t_att_stu", "张三_简历.pdf", PDF_BYTES, "RESUME", 200).path("data").path("id").asString();
        String other = upload("t_att_stu", "获奖证书.png", PNG_BYTES, "OTHER", 200).path("data").path("id").asString();
        String foreign = upload("alex.chen", "x.pdf", PDF_BYTES, "OTHER", 200).path("data").path("id").asString();

        // 别人的附件不能挂到自己的申请上
        apiPost("/api/applications", "t_att_stu",
                Map.of("jobId", "T_ATT_J1", "statement", "测试", "attachmentIds", java.util.List.of(foreign)), 400);

        JsonNode r = apiPost("/api/applications", "t_att_stu", Map.of("jobId", "T_ATT_J1", "statement", "测试",
                "attachmentIds", java.util.List.of(resume, other, resume)), 200);
        JsonNode app = r.path("data");
        assertThat(app.path("attachmentIds").size()).isEqualTo(2);
        assertThat(app.path("attachmentIds").get(0).asString()).isEqualTo(resume);
        assertThat(app.path("resumeFileName").asString()).isEqualTo("张三_简历.pdf");
        String appId = app.path("id").asString();
        assertThat(applicationMapper.selectById(appId).getAttachmentIds()).containsExactly(resume, other);

        // 导师在筛选抽屉按申请取附件并下载（申请人是外院学生，凭投递关系可见）
        JsonNode list = send(org.springframework.http.HttpMethod.GET, "/api/attachments?applicationId=" + appId,
                "zhang.mingyuan", null, 200);
        assertThat(list.path("data").size()).isEqualTo(2);
        assertThat(download("zhang.mingyuan", other).getStatus()).isEqualTo(200);
        // 数学学院导师与这份申请无关
        send(org.springframework.http.HttpMethod.GET, "/api/attachments?applicationId=" + appId,
                "chen.shouren", null, 404);
    }
}
