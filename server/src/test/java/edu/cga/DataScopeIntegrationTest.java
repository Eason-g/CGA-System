package edu.cga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import edu.cga.common.BizException;
import edu.cga.domain.mapper.JobMapper;
import edu.cga.domain.mapper.UserMapper;
import edu.cga.scope.DataScope;
import edu.cga.security.CurrentUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

/**
 * 设计文档第 9 节第 2 条及补充：数据隔离在 SQL 层生效，各角色只能看到自己范围内的数据。
 */
class DataScopeIntegrationTest extends IntegrationTestBase {

    @Autowired
    private DataScope dataScope;

    @Autowired
    private JobMapper jobMapper;

    @Autowired
    private UserMapper userMapper;

    private static List<String> values(JsonNode array, String field) {
        List<String> out = new ArrayList<>();
        array.forEach(n -> out.add(n.path(field).asString()));
        return out;
    }

    private static List<String> ids(JsonNode array) {
        return values(array, "id");
    }

    // ---------------------------------------------------------------- 导师：院系隔离

    @Test
    void mathMentorDoesNotSeeComputerScienceJobs() throws Exception {
        JsonNode db = snapshotAs("chen.shouren");
        assertThat(values(db.path("jobs"), "deptId")).isNotEmpty().doesNotContain("DEPT_CS");
        assertThat(ids(db.path("jobs"))).containsExactly("JOB_08");
        assertThat(ids(db.path("applications"))).containsExactly("APP_06");
        assertThat(ids(db.path("contracts"))).containsExactly("CON_05");
        assertThat(ids(db.path("payrolls"))).containsExactly("PAY_06");
        assertThat(ids(db.path("budgets"))).containsExactlyInAnyOrder("B_MATH_TA", "B_GA_SCHOOL");
        assertThat(ids(db.path("users"))).containsExactlyInAnyOrder("U_MENTOR_MATH", "U_STU_ELENA");
        // 公共数据所有人可见
        assertThat(db.path("departments").size()).isEqualTo(4);
        assertThat(db.path("configs").has("workflows")).isTrue();
    }

    @Test
    void computerScienceMentorDoesNotSeeOtherDepartmentsJobs() throws Exception {
        JsonNode db = snapshotAs("zhang.mingyuan");
        assertThat(values(db.path("jobs"), "deptId")).containsOnly("DEPT_CS");
        assertThat(ids(db.path("jobs"))).doesNotContain("JOB_05", "JOB_08", "JOB_09");
    }

    @Test
    void lookupOfOtherDepartmentsJobByIdIsNotFound() {
        CurrentUser mathMentor = CurrentUser.from(userMapper.selectById("U_MENTOR_MATH"));
        // 本院系岗位可以取到
        assertThat(dataScope.requireVisible(jobMapper, dataScope.jobs(mathMentor), "JOB_08", "岗位不存在").getId())
                .isEqualTo("JOB_08");
        // 外院岗位存在，但不在数据范围内，按不存在处理
        assertThatThrownBy(() -> dataScope.requireVisible(jobMapper, dataScope.jobs(mathMentor), "JOB_01", "岗位不存在"))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo(404));
    }

    @Test
    void mentorReviewingOtherDepartmentsJobGets404() throws Exception {
        // JOB_06 是计算机学院待院系初审的岗位，数学学院导师按 ID 审核时当作不存在
        String token = login("chen.shouren");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/jobs/JOB_06/review")
                        .header("Authorization", bearer(token))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVE\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.message")
                        .value("岗位不存在"));
        assertThat(jobMapper.selectById("JOB_06").getStatus()).isEqualTo("PENDING_DEPT_REVIEW");
    }

    @Test
    void otherUsersContactInfoIsMaskedForMentor() throws Exception {
        JsonNode db = snapshotAs("chen.shouren");
        JsonNode elena = null;
        for (JsonNode u : db.path("users")) {
            if ("U_STU_ELENA".equals(u.path("id").asString())) {
                elena = u;
            }
        }
        assertThat(elena).isNotNull();
        assertThat(elena.path("phone").asString()).isEqualTo("137****3319");
        assertThat(elena.path("email").asString()).isEqualTo("e***@univ.edu.cn");
    }

    // ---------------------------------------------------------------- 学生：只看自己的

    @Test
    void studentSeesOnlyOwnApplicationsAndPayrolls() throws Exception {
        JsonNode db = snapshotAs("lin.zhiyuan");
        assertThat(values(db.path("applications"), "studentId")).containsOnly("U_CN_STU");
        assertThat(ids(db.path("applications"))).containsExactly("APP_01");
        assertThat(values(db.path("payrolls"), "studentId")).containsOnly("U_CN_STU");
        assertThat(ids(db.path("payrolls"))).containsExactlyInAnyOrder("PAY_01", "PAY_03");
        assertThat(values(db.path("contracts"), "studentId")).containsOnly("U_CN_STU");
        assertThat(values(db.path("timesheets"), "studentId")).containsOnly("U_CN_STU");
        assertThat(values(db.path("evaluations"), "studentId")).containsOnly("U_CN_STU");
        assertThat(values(db.path("notifications"), "userId")).containsOnly("U_CN_STU");

        // 只能看到已发布岗位（待审岗位 JOB_06、JOB_07 不可见）
        assertThat(values(db.path("jobs"), "status")).containsOnly("PUBLISHED");
        assertThat(ids(db.path("jobs"))).doesNotContain("JOB_06", "JOB_07");

        // 用户表只有本人和岗位导师，看不到其他学生
        assertThat(ids(db.path("users"))).contains("U_CN_STU", "U_MENTOR")
                .doesNotContain("U_INTL_STU", "U_STU_SU", "U_STU_ELENA");
        for (JsonNode u : db.path("users")) {
            if (!"U_CN_STU".equals(u.path("id").asString())) {
                // 导师只给姓名、职称、院系
                assertThat(u.has("email")).isFalse();
                assertThat(u.has("phone")).isFalse();
                assertThat(u.has("name")).isTrue();
            }
        }

        // 经费只含自己薪酬引用的，且不含金额
        assertThat(ids(db.path("budgets"))).containsExactly("B_CS_RA");
        assertThat(db.path("budgets").get(0).path("totalAmount").isNull()).isTrue();

        // 审计日志只含与自己记录相关的，不含岗位审批日志
        assertThat(values(db.path("auditLogs"), "targetType")).doesNotContain("JOB");
        assertThat(ids(db.path("auditLogs"))).containsExactlyInAnyOrder("LOG_07", "LOG_09", "LOG_10", "LOG_11");
    }

    @Test
    void anotherStudentCannotSeeLinsRecords() throws Exception {
        JsonNode db = snapshotAs("alex.chen");
        assertThat(ids(db.path("applications"))).containsExactlyInAnyOrder("APP_02", "APP_03");
        assertThat(ids(db.path("payrolls"))).doesNotContain("PAY_01", "PAY_03", "PAY_05", "PAY_06");
        assertThat(ids(db.path("contracts"))).containsExactlyInAnyOrder("CON_02", "CON_03");
    }

    // ---------------------------------------------------------------- 院系管理员 / 国际生办

    @Test
    void deptAdminSeesOnlyOwnDepartment() throws Exception {
        JsonNode db = snapshotAs("li.wanqing");
        assertThat(values(db.path("jobs"), "deptId")).containsOnly("DEPT_CS");
        assertThat(values(db.path("payrolls"), "deptId")).containsOnly("DEPT_CS");
        assertThat(values(db.path("budgets"), "deptId")).containsOnly("DEPT_CS");
        assertThat(ids(db.path("applications"))).doesNotContain("APP_06");
    }

    @Test
    void intlOfficerSeesInternationalAndSpecialApprovalRecords() throws Exception {
        JsonNode db = snapshotAs("wang.haitao");
        assertThat(ids(db.path("applications"))).containsExactlyInAnyOrder("APP_02", "APP_03", "APP_06");
        assertThat(ids(db.path("contracts"))).containsExactlyInAnyOrder("CON_02", "CON_03", "CON_05");
        assertThat(db.path("payrolls").size()).isZero();
        assertThat(db.path("budgets").size()).isZero();
        assertThat(ids(db.path("users"))).contains("U_INTL_STU", "U_STU_ELENA").doesNotContain("U_CN_STU");
    }
}
