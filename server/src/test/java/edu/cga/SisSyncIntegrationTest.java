package edu.cga;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import edu.cga.domain.entity.AuditLog;
import edu.cga.domain.entity.User;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import tools.jackson.databind.JsonNode;

/**
 * P2 B.2 学籍同步：非授权角色 403、差异清单（谁、字段、旧值→新值）正确并写审计、再次同步无差异、
 * 休学学生申请和录用被拦截（硬性条件，不可特批）。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SisSyncIntegrationTest extends WriteApiTestBase {

    private static final String SCHOOL = "zhou.guoqiang";
    private static final String INTL = "wang.haitao";
    private static final String MENTOR = "zhang.mingyuan";
    private static final String SUSPENDED = "su.yutong";

    private static List<String> diffKeys(JsonNode diffs) {
        List<String> keys = new ArrayList<>();
        diffs.forEach(d -> keys.add(d.path("userId").asString() + "|" + d.path("field").asString() + "|"
                + d.path("before").asString() + "→" + d.path("after").asString()));
        return keys;
    }

    @Test
    @Order(1)
    void unauthorizedRolesGet403() throws Exception {
        apiPost("/api/integration/sis/sync", "lin.zhiyuan", null, 403);
        apiPost("/api/integration/sis/sync", MENTOR, null, 403);
        apiPost("/api/integration/sis/sync", "li.wanqing", null, 403);
        apiPost("/api/integration/sis/sync", null, null, 401);
        assertThat(userMapper.selectById("U_STU_SU").getEnrollmentStatus()).isEqualTo("在读");
    }

    @Test
    @Order(2)
    void intlOfficerOnlySyncsInternationalStudents() throws Exception {
        JsonNode d = apiPost("/api/integration/sis/sync", INTL, null, 200).path("data");
        assertThat(diffKeys(d.path("diffs"))).containsExactly("U_STU_ELENA|visaExpiry|2027-06-30→2028-06-30");
        // 国内生（苏雨桐休学）不在国际生办范围内，不更新
        assertThat(userMapper.selectById("U_STU_SU").getEnrollmentStatus()).isEqualTo("在读");
        // 还原，后面由校级管理员完整同步
        User elena = userMapper.selectById("U_STU_ELENA");
        elena.setVisaExpiry(LocalDate.of(2027, 6, 30));
        userMapper.updateById(elena);
    }

    @Test
    @Order(3)
    void schoolAdminSyncReturnsDiffsAndWritesAudit() throws Exception {
        // 休学前已投递、待录用的申请（用于后面验证录用被拦截）
        createApplication("T_APP_SU", "JOB_04", "U_STU_SU", "SUBMITTED");
        long userLogs = auditLogMapper.selectCount(new QueryWrapper<AuditLog>()
                .eq("action", "学籍系统同步").eq("target_type", "USER"));
        long summaryLogs = auditLogMapper.selectCount(new QueryWrapper<AuditLog>()
                .eq("action", "学籍系统同步").eq("target_type", "INTEGRATION"));

        JsonNode r = apiPost("/api/integration/sis/sync", SCHOOL, null, 200);
        JsonNode d = r.path("data");
        assertThat(d.path("source").asString()).isEqualTo("模拟学籍系统");
        assertThat(diffKeys(d.path("diffs"))).containsExactlyInAnyOrder(
                "U_STU_SU|enrollmentStatus|在读→休学",
                "U_STU_ELENA|visaExpiry|2027-06-30→2028-06-30");
        assertThat(d.path("changedStudents").asInt()).isEqualTo(2);
        assertThat(d.path("checked").asInt()).isEqualTo(4);
        assertThat(d.path("unmatched").get(0).asString()).startsWith("2026990001");
        JsonNode suDiff = d.path("diffs").get(0).path("userId").asString().equals("U_STU_SU")
                ? d.path("diffs").get(0) : d.path("diffs").get(1);
        assertThat(suDiff.path("name").asString()).isEqualTo("苏雨桐");
        assertThat(suDiff.path("fieldLabel").asString()).isEqualTo("学籍状态");
        assertThat(r.path("message").asString()).contains("更新 2 名学生");

        assertThat(userMapper.selectById("U_STU_SU").getEnrollmentStatus()).isEqualTo("休学");
        assertThat(userMapper.selectById("U_STU_ELENA").getVisaExpiry()).isEqualTo(LocalDate.of(2028, 6, 30));
        assertThat(auditLogMapper.selectCount(new QueryWrapper<AuditLog>()
                .eq("action", "学籍系统同步").eq("target_type", "USER"))).isEqualTo(userLogs + 2);
        assertThat(auditLogMapper.selectCount(new QueryWrapper<AuditLog>()
                .eq("action", "学籍系统同步").eq("target_type", "INTEGRATION"))).isEqualTo(summaryLogs + 1);
        AuditLog suLog = auditLogMapper.selectList(new QueryWrapper<AuditLog>()
                .eq("action", "学籍系统同步").eq("target_id", "U_STU_SU")).get(0);
        assertThat(suLog.getOpinion()).contains("学籍状态 在读 → 休学");
        assertThat(suLog.getOperatorId()).isEqualTo("U_SCHOOL_ADMIN");
    }

    @Test
    @Order(4)
    void secondSyncHasNoDiff() throws Exception {
        JsonNode r = apiPost("/api/integration/sis/sync", SCHOOL, null, 200);
        assertThat(r.path("data").path("diffs").size()).isZero();
        assertThat(r.path("message").asString()).contains("无差异");
    }

    @Test
    @Order(5)
    void suspendedStudentCannotApplyEvenWithSpecialApproval() throws Exception {
        Map<String, Object> body = Map.of("jobId", "JOB_10", "statement", "申请", "isSpecialApproval", false);
        JsonNode r = apiPost("/api/applications", SUSPENDED, body, 403);
        JsonNode first = r.path("data").path("checks").get(0);
        assertThat(first.path("key").asString()).isEqualTo("enrollment");
        assertThat(first.path("overridable").asBoolean()).isFalse();
        assertThat(first.path("detail").asString())
                .isEqualTo("当前学籍状态为「休学」，仅在读学生可申请三助岗位（硬性条件，不可特批）");

        Map<String, Object> special = Map.of("jobId", "JOB_10", "statement", "申请", "isSpecialApproval", true,
                "specialReason", "希望特批通过学籍限制");
        JsonNode s = apiPost("/api/applications", SUSPENDED, special, 403);
        assertThat(s.path("message").asString()).contains("学籍状态校验（在读）未通过，属于硬性条件，不可特批");
    }

    @Test
    @Order(6)
    void suspendedStudentCannotBeHired() throws Exception {
        JsonNode r = apiPost("/api/applications/T_APP_SU/decision", MENTOR,
                Map.of("score", 90, "decision", "HIRE", "comment", ""), 403);
        assertThat(r.path("message").asString()).contains("该学生当前学籍状态为「休学」，仅在读学生可录用（不可特批）");
        assertThat(applicationMapper.selectById("T_APP_SU").getStatus()).isEqualTo("SUBMITTED");
    }
}
