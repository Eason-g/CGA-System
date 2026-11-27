package edu.cga;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import edu.cga.common.TimeService;
import edu.cga.domain.entity.Job;
import edu.cga.domain.entity.User;
import edu.cga.rule.RuleEngine;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import tools.jackson.databind.JsonNode;

/**
 * P1 第三批 3.2 合规强化：语言证明核验、签证有效期覆盖聘期。
 */
class ComplianceIntegrationTest extends MaterialFinanceTestBase {

    private static final String INTL = "wang.haitao";
    private static final String MENTOR = "zhang.mingyuan";

    @Autowired
    private TimeService time;

    /** 建一个要求 IELTS 6.5 的已发布岗位（面向国际生） */
    private Job langJob(String id) {
        Job j = createPublishedJob(id, "U_MENTOR", "DEPT_CS", "B_CS_RA", 6, true);
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("minLangScore", 6.5);
        extra.put("languageRequirement", "IELTS 6.5 或同等水平");
        j.setExtraFields(extra);
        jobMapper.updateById(j);
        return j;
    }

    private static JsonNode check(JsonNode resp, String key) {
        for (JsonNode c : resp.path("data").path("checks")) {
            if (key.equals(c.path("key").asString())) {
                return c;
            }
        }
        throw new AssertionError("缺少检查项 " + key);
    }

    @Test
    void languageCertPassesAfterIntlOfficeVerification() throws Exception {
        langJob("T_LANG_J1");
        // Elena：IELTS 6.0，低于岗位要求；上传新的语言证明后待核验
        String att = upload("elena.rossi", "IELTS_7.pdf", PDF_BYTES, "LANG_CERT", 200).path("data").path("id").asString();
        User pending = userMapper.selectById("U_STU_ELENA");
        assertThat(pending.getLanguageCertStatus()).isEqualTo("PENDING");
        assertThat(pending.getLanguageValid()).isFalse();

        JsonNode blocked = apiPost("/api/applications", "elena.rossi",
                Map.of("jobId", "T_LANG_J1", "statement", "测试"), 403);
        JsonNode lang = check(blocked, "lang");
        assertThat(lang.path("passed").asBoolean()).isFalse();
        assertThat(lang.path("overridable").asBoolean()).isTrue();
        assertThat(lang.path("detail").asString()).contains("待国际生办核验");

        // 合规核验列表里能看到这份待核验材料
        JsonNode list = send(HttpMethod.GET, "/api/compliance/materials?status=PENDING", INTL, null, 200);
        assertThat(list.path("data").toString()).contains(att).contains("U_STU_ELENA");

        long logs = auditCount();
        JsonNode v = apiPost("/api/users/U_STU_ELENA/compliance-verify", INTL,
                Map.of("attachmentId", att, "decision", "APPROVE", "languageLevel", 7.0, "languageCert", "IELTS 7.0",
                        "opinion", "原件核对一致"), 200);
        assertThat(v.path("data").path("student").path("languageValid").asBoolean()).isTrue();
        assertThat(v.path("data").path("attachment").path("verifyStatus").asString()).isEqualTo("VERIFIED");
        assertThat(auditCount()).isEqualTo(logs + 1);
        User verified = userMapper.selectById("U_STU_ELENA");
        assertThat(verified.getLanguageLevel()).isEqualByComparingTo(new BigDecimal("7.0"));
        assertThat(verified.getLanguageCertStatus()).isEqualTo("VERIFIED");

        // 重复核验同一份材料 400
        apiPost("/api/users/U_STU_ELENA/compliance-verify", INTL, Map.of("attachmentId", att, "decision", "APPROVE"),
                400);

        JsonNode ok = apiPost("/api/applications", "elena.rossi", Map.of("jobId", "T_LANG_J1", "statement", "测试"), 200);
        assertThat(ok.path("data").path("status").asString()).isEqualTo("SUBMITTED");
    }

    @Test
    void unverifiedLanguageCertFailsButCanBeSpeciallyApproved() throws Exception {
        langJob("T_LANG_J2");
        createStudent("T_LANG_S", "DEPT_CS", true, true); // 原 IELTS 7.0 有效，本可通过
        upload("t_lang_s", "新语言证明.jpg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10},
                "LANG_CERT", 200);

        // 新证明未核验：语言项不通过，但属于可特批项
        JsonNode blocked = apiPost("/api/applications", "t_lang_s", Map.of("jobId", "T_LANG_J2", "statement", "测试"),
                403);
        assertThat(check(blocked, "lang").path("passed").asBoolean()).isFalse();
        assertThat(blocked.path("data").path("canSpecialApprove").asBoolean()).isTrue();

        JsonNode special = apiPost("/api/applications", "t_lang_s", Map.of("jobId", "T_LANG_J2", "statement", "测试",
                "isSpecialApproval", true, "specialReason", "语言证明已提交原件，待国际生办核验"), 200);
        assertThat(special.path("data").path("status").asString()).isEqualTo("PENDING_SPECIAL_APPROVAL");
    }

    @Test
    void nonIntlOfficeCannotVerify() throws Exception {
        createStudent("T_LANG_X", "DEPT_CS", true, true);
        String att = upload("t_lang_x", "toefl.pdf", PDF_BYTES, "LANG_CERT", 200).path("data").path("id").asString();
        Map<String, Object> body = Map.of("attachmentId", att, "decision", "APPROVE");
        apiPost("/api/users/T_LANG_X/compliance-verify", MENTOR, body, 403);
        apiPost("/api/users/T_LANG_X/compliance-verify", "li.wanqing", body, 403);
        apiPost("/api/users/T_LANG_X/compliance-verify", "t_lang_x", body, 403);
        send(HttpMethod.GET, "/api/compliance/materials", MENTOR, null, 403);
        assertThat(userMapper.selectById("T_LANG_X").getLanguageValid()).isFalse();

        // 国际生办不能核验国内生的材料（不在范围内 404）；校级管理员可以
        createStudent("T_LANG_CN", "DEPT_CS", false, true);
        String cn = upload("t_lang_cn", "cet6.pdf", PDF_BYTES, "LANG_CERT", 200).path("data").path("id").asString();
        apiPost("/api/users/T_LANG_CN/compliance-verify", INTL, Map.of("attachmentId", cn, "decision", "APPROVE"), 404);
        apiPost("/api/users/T_LANG_CN/compliance-verify", "zhou.guoqiang",
                Map.of("attachmentId", cn, "decision", "REJECT", "opinion", "证书编号查询不到"), 200);
        assertThat(userMapper.selectById("T_LANG_CN").getLanguageCertStatus()).isEqualTo("REJECTED");
    }

    @Test
    void visaExpiringBeforeContractEndBlocksHireAndCannotBeOverridden() throws Exception {
        User s = createStudent("T_VISA_S", "DEPT_CS", true, true);
        s.setVisaExpiry(time.today().plusDays(30));
        userMapper.updateById(s);
        createPublishedJob("T_VISA_J1", "U_MENTOR", "DEPT_CS", "B_CS_RA", 6, true);
        createPublishedJob("T_VISA_J2", "U_MENTOR", "DEPT_CS", "B_CS_RA", 6, true);

        // 申请阶段只提示、不拦截
        JsonNode applied = apiPost("/api/applications", "t_visa_s", Map.of("jobId", "T_VISA_J1", "statement", "测试"),
                200);
        JsonNode hint = applied.path("data").path("complianceCheckResult").path("hints").get(0);
        assertThat(hint.path("key").asString()).isEqualTo("visaPeriod");
        assertThat(hint.path("passed").asBoolean()).isFalse();
        String appId = applied.path("data").path("id").asString();

        java.time.LocalDate end = RuleEngine.predictContractEnd(time.today(), 16);
        String expected = "该国际生签证有效期至 " + time.today().plusDays(30) + "，早于聘期结束日 " + end
                + "，签证须覆盖整个聘期，不能录用（不可特批）";
        Map<String, Object> hire = Map.of("score", 90, "decision", "HIRE", "comment", "");
        JsonNode r = apiPost("/api/applications/" + appId + "/decision", MENTOR, hire, 403);
        assertThat(r.path("message").asString()).isEqualTo(expected);
        assertThat(applicationMapper.selectById(appId).getStatus()).isEqualTo("SUBMITTED");
        assertThat(contractMapper.selectAllocatingByStudent("T_VISA_S")).isEmpty();

        // 即使申请已获国际生办特批，签证覆盖仍不能放行
        createApplication("T_VISA_APP2", "T_VISA_J2", "T_VISA_S", "SPECIAL_APPROVED");
        var a2 = applicationMapper.selectById("T_VISA_APP2");
        a2.setIsSpecialApproval(true);
        a2.setSpecialStatus("APPROVED");
        a2.setComplianceCheckResult(Map.of("passed", false, "projectedHours", 40));
        applicationMapper.updateById(a2);
        JsonNode r2 = apiPost("/api/applications/T_VISA_APP2/decision", "zhou.guoqiang", hire, 403);
        assertThat(r2.path("message").asString()).isEqualTo(expected);

        // 签证续签后（有效期覆盖聘期）可以录用
        s.setVisaExpiry(end.plusDays(1));
        userMapper.updateById(s);
        apiPost("/api/applications/" + appId + "/decision", MENTOR, hire, 200);
    }
}
