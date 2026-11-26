package edu.cga;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import edu.cga.domain.entity.Notification;
import edu.cga.domain.mapper.NotificationMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

/**
 * 写接口权限反例：每个接口至少一个“错角色 403”或“不在数据范围内 404”，外加参数校验 400。
 * 设计文档第 9 节第 2 条补充：导师审核外院岗位返回 404。
 * 所有用例只验证被拒绝的请求，不改变数据（被拒绝的请求在事务内回滚）。
 */
class WritePermissionIntegrationTest extends WriteApiTestBase {

    private static final String STUDENT_LIN = "lin.zhiyuan";
    private static final String STUDENT_ALEX = "alex.chen";
    private static final String MENTOR_CS = "zhang.mingyuan";
    private static final String MENTOR_MATH = "chen.shouren";
    private static final String DEPT_ADMIN = "li.wanqing";
    private static final String INTL = "wang.haitao";
    private static final String SCHOOL = "zhou.guoqiang";

    @Autowired
    private NotificationMapper notificationMapper;

    private static Map<String, Object> jobDraft(String deptId, String budgetId) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("type", "TA");
        d.put("title", "权限测试岗位");
        d.put("deptId", deptId);
        d.put("budgetId", budgetId);
        d.put("quota", 1);
        d.put("hourlyRate", 40);
        d.put("weeklyHours", 4);
        d.put("weeks", 4);
        return d;
    }

    // ---------------------------------------------------------------- 岗位

    @Test
    void createJobPermissions() throws Exception {
        apiPost("/api/jobs", STUDENT_LIN, jobDraft("DEPT_CS", "B_CS_TA"), 403);
        apiPost("/api/jobs", INTL, jobDraft("DEPT_CS", "B_CS_TA"), 403);
        JsonNode other = apiPost("/api/jobs", MENTOR_CS, jobDraft("DEPT_MATH", "B_MATH_TA"), 403);
        assertThat(other.path("message").asString()).isEqualTo("只能为本院系发布岗位");
        // 本院系建岗但选了不可见的外院经费 → 404
        JsonNode budget = apiPost("/api/jobs", MENTOR_CS, jobDraft("DEPT_CS", "B_MATH_TA"), 404);
        assertThat(budget.path("message").asString()).isEqualTo("经费来源账号不存在");
        // 参数校验
        Map<String, Object> badType = jobDraft("DEPT_CS", "B_CS_TA");
        badType.put("type", "XA");
        assertThat(apiPost("/api/jobs", MENTOR_CS, badType, 400).path("message").asString())
                .isEqualTo("岗位类型必须为 TA、RA 或 GA");
        Map<String, Object> badQuota = jobDraft("DEPT_CS", "B_CS_TA");
        badQuota.put("quota", 0);
        assertThat(apiPost("/api/jobs", MENTOR_CS, badQuota, 400).path("message").asString())
                .isEqualTo("招聘人数必须为正整数");
        Map<String, Object> missing = jobDraft("DEPT_CS", "B_CS_TA");
        missing.remove("hourlyRate");
        apiPost("/api/jobs", MENTOR_CS, missing, 400);
    }

    @Test
    void reviewJobPermissions() throws Exception {
        // 数学学院导师审核计算机学院的待审岗位：不在数据范围内，404
        JsonNode foreign = apiPost("/api/jobs/JOB_06/review", MENTOR_MATH, Map.of("decision", "APPROVE"), 404);
        assertThat(foreign.path("message").asString()).isEqualTo("岗位不存在");
        // 本院系导师能看到但不是审批角色，403
        apiPost("/api/jobs/JOB_06/review", MENTOR_CS, Map.of("decision", "APPROVE"), 403);
        // 国际生办能看到 JOB_06（面向国际生），但当前是院系初审节点，403
        JsonNode node = apiPost("/api/jobs/JOB_06/review", INTL, Map.of("decision", "APPROVE"), 403);
        assertThat(node.path("message").asString()).isEqualTo("当前流程节点为「待院系初审」，需由院系管理员审批");
        // 学生看不到待审岗位，404
        apiPost("/api/jobs/JOB_06/review", STUDENT_LIN, Map.of("decision", "APPROVE"), 404);
        // decision 取值非法
        apiPost("/api/jobs/JOB_06/review", DEPT_ADMIN, Map.of("decision", "OK"), 400);
        assertThat(jobMapper.selectById("JOB_06").getStatus()).isEqualTo("PENDING_DEPT_REVIEW");
    }

    @Test
    void toggleMilestonePermissions() throws Exception {
        apiPost("/api/jobs/JOB_04/milestones/MS_02/toggle", DEPT_ADMIN, null, 403);
        apiPost("/api/jobs/JOB_04/milestones/MS_02/toggle", MENTOR_MATH, null, 404);
        apiPost("/api/jobs/JOB_04/milestones/MS_404/toggle", MENTOR_CS, null, 404);
    }

    // ---------------------------------------------------------------- 申请

    @Test
    void applicationPermissions() throws Exception {
        // 投递：只有学生
        apiPost("/api/applications", MENTOR_CS, Map.of("jobId", "JOB_02"), 403);
        // 学生看不到待审岗位 → 404
        apiPost("/api/applications", STUDENT_LIN, Map.of("jobId", "JOB_06"), 404);
        // 特批审核：导师 403
        apiPost("/api/applications/APP_06/special-review", MENTOR_MATH, Map.of("decision", "APPROVE"), 403);
        // 安排面试：学生 403；外院导师看不到 APP_05 → 404
        Map<String, Object> iv = Map.of("time", "2026-11-25 10:00", "location", "信息楼 412");
        apiPost("/api/applications/APP_05/interview", STUDENT_LIN, iv, 403);
        apiPost("/api/applications/APP_05/interview", MENTOR_MATH, iv, 404);
        // 已录用的申请不能再安排面试
        apiPost("/api/applications/APP_01/interview", MENTOR_CS, iv, 400);
        // 录用决定：院系管理员 403；外院导师 404；参数非法 400
        Map<String, Object> hire = Map.of("score", 90, "decision", "HIRE");
        apiPost("/api/applications/APP_05/decision", DEPT_ADMIN, hire, 403);
        apiPost("/api/applications/APP_05/decision", MENTOR_MATH, hire, 404);
        apiPost("/api/applications/APP_05/decision", MENTOR_CS, Map.of("score", 101, "decision", "HIRE"), 400);
        apiPost("/api/applications/APP_05/decision", MENTOR_CS, Map.of("score", 90, "decision", "MAYBE"), 400);
        assertThat(applicationMapper.selectById("APP_05").getStatus()).isEqualTo("SUBMITTED");
    }

    // ---------------------------------------------------------------- 合同 / 工时

    @Test
    void contractAndTimesheetPermissions() throws Exception {
        Map<String, Object> sig = Map.of("signatureDataUrl", "data:image/png;base64,AAAA", "complianceAgreed", true);
        // 签约：导师 403；别人的合同 404
        apiPost("/api/contracts/CON_03/sign", MENTOR_CS, sig, 403);
        apiPost("/api/contracts/CON_03/sign", STUDENT_LIN, sig, 404);
        // 国际生不勾选合规声明不能签
        JsonNode noAgree = apiPost("/api/contracts/CON_03/sign", STUDENT_ALEX,
                Map.of("signatureDataUrl", "data:image/png;base64,AAAA", "complianceAgreed", false), 400);
        assertThat(noAgree.path("message").asString()).isEqualTo("国际生必须勾选《国际生合法工时合规声明》方可生效");
        apiPost("/api/contracts/CON_03/sign", STUDENT_ALEX, Map.of("signatureDataUrl", ""), 400);

        // 填报：导师 403；别人的合同 404；待签合同 400；工时非正 400
        Map<String, Object> ts = Map.of("contractId", "CON_02", "weekNo", 12, "hours", 2, "workLog", "测试");
        apiPost("/api/timesheets", MENTOR_CS, ts, 403);
        apiPost("/api/timesheets", STUDENT_LIN, ts, 404);
        JsonNode pending = apiPost("/api/timesheets", STUDENT_ALEX,
                Map.of("contractId", "CON_03", "weekNo", 12, "hours", 2, "workLog", "测试"), 400);
        assertThat(pending.path("message").asString()).isEqualTo("请先签署电子聘用协议后再填报工时");
        JsonNode zero = apiPost("/api/timesheets", STUDENT_ALEX,
                Map.of("contractId", "CON_02", "weekNo", 12, "hours", 0, "workLog", "测试"), 400);
        assertThat(zero.path("message").asString()).isEqualTo("填报工时必须大于 0 小时");

        // 审核：学生 403；外院导师 404；院系管理员可审本院系（这里只验证外院院系管理员看不到数学学院的）
        apiPost("/api/timesheets/TS_03/review", STUDENT_LIN, Map.of("decision", "APPROVE"), 403);
        apiPost("/api/timesheets/TS_03/review", MENTOR_MATH, Map.of("decision", "APPROVE"), 404);
        apiPost("/api/timesheets/TS_08/review", DEPT_ADMIN, Map.of("decision", "APPROVE"), 404);
        // 已审核的不能重复审核
        apiPost("/api/timesheets/TS_01/review", MENTOR_CS, Map.of("decision", "REJECT"), 400);
        assertThat(timesheetMapper.selectById("TS_03").getStatus()).isEqualTo("PENDING");
    }

    // ---------------------------------------------------------------- 薪酬 / 考核 / 配置

    @Test
    void payrollEvaluationAndConfigPermissions() throws Exception {
        apiPost("/api/payrolls/generate", MENTOR_CS, Map.of("month", "2026-11"), 403);
        apiPost("/api/payrolls/generate", STUDENT_LIN, null, 403);
        apiPost("/api/payrolls/generate", SCHOOL, Map.of("month", "2026/11"), 400);
        apiPost("/api/payrolls/PAY_03/payout", DEPT_ADMIN, null, 403);
        apiPost("/api/payrolls/PAY_404/payout", SCHOOL, null, 404);

        Map<String, Object> ev = Map.of("contractId", "CON_01", "dimensionScores", Map.of("mentor", 90, "service", 90));
        apiPost("/api/evaluations", STUDENT_LIN, ev, 403);
        apiPost("/api/evaluations", MENTOR_MATH, ev, 404);
        apiPost("/api/evaluations", MENTOR_CS, Map.of("contractId", "CON_01",
                "dimensionScores", Map.of("mentor", 120)), 400);

        apiPut("/api/configs", DEPT_ADMIN, Map.of("globalRules", Map.of("maxWeeklyHoursIntl", 12)), 403);
        apiPut("/api/configs", MENTOR_CS, Map.of("globalRules", Map.of("maxWeeklyHoursIntl", 12)), 403);
        assertThat(payrollMapper.selectById("PAY_03").getFinanceStatus()).isEqualTo("PENDING_PAY");
    }

    // ---------------------------------------------------------------- 本人接口

    @Test
    void notificationsAndProfileOnlyAffectSelf() throws Exception {
        apiPost("/api/notifications/read-all", null, null, 401);
        apiPut("/api/users/me", null, Map.of("bio", "x"), 401);

        // 林致远标记已读，Alex 的未读通知不受影响
        JsonNode read = apiPost("/api/notifications/read-all", STUDENT_LIN, Map.of("userId", "U_INTL_STU"), 200);
        assertThat(read.path("message").asString()).isEqualTo("已全部标为已读");
        assertThat(notificationMapper.selectCount(new QueryWrapper<Notification>()
                .eq("user_id", "U_CN_STU").eq("is_read", 0))).isZero();
        assertThat(notificationMapper.selectCount(new QueryWrapper<Notification>()
                .eq("user_id", "U_INTL_STU").eq("is_read", 0))).isEqualTo(2);

        // 前端传来的 userId 被忽略，只改登录用户本人
        JsonNode me = apiPut("/api/users/me", STUDENT_LIN, Map.of("userId", "U_INTL_STU", "phone", " 139-0000-1111 ",
                "skills", List.of("Java", " ", "MySQL"), "bio", " 你好 "), 200);
        assertThat(me.path("message").asString()).isEqualTo("个人主页信息已保存");
        assertThat(me.path("data").path("id").asString()).isEqualTo("U_CN_STU");
        assertThat(me.path("data").path("phone").asString()).isEqualTo("139-0000-1111");
        assertThat(me.path("data").path("bio").asString()).isEqualTo("你好");
        assertThat(me.path("data").path("skills").size()).isEqualTo(2);
        assertThat(me.path("data").has("passwordHash")).isFalse();
        assertThat(userMapper.selectById("U_INTL_STU").getPhone()).isEqualTo("138-0010-2091");
        apiPut("/api/users/me", STUDENT_LIN, Map.of("email", "not-an-email"), 400);
    }

    // ---------------------------------------------------------------- 快照：跨岗总负荷

    @Test
    void hoursSummaryShowsCrossDepartmentLoadWithoutContractDetails() throws Exception {
        // Elena（数学学院）在计算机学院另有一份 6h 合同
        var job = createPublishedJob("T_J_ELENA", "U_MENTOR", "DEPT_CS", "B_CS_RA", 6, true);
        createActiveContract("T_CON_ELENA", "U_STU_ELENA", job);

        JsonNode db = snapshotAs(MENTOR_MATH);
        JsonNode elena = null;
        for (JsonNode u : db.path("users")) {
            if ("U_STU_ELENA".equals(u.path("id").asString())) {
                elena = u;
            }
        }
        assertThat(elena).isNotNull();
        // 总负荷按全校合同计算：数学 10h + 计算机 6h
        assertThat(elena.path("hoursSummary").path("allocated").asInt()).isEqualTo(16);
        assertThat(elena.path("hoursSummary").path("max").asInt()).isEqualTo(20);
        // 但看不到计算机学院那份合同的明细
        List<String> contractIds = new java.util.ArrayList<>();
        db.path("contracts").forEach(c -> contractIds.add(c.path("id").asString()));
        assertThat(contractIds).containsExactly("CON_05");
        // 非学生用户没有 hoursSummary
        for (JsonNode u : db.path("users")) {
            if ("U_MENTOR_MATH".equals(u.path("id").asString())) {
                assertThat(u.has("hoursSummary")).isFalse();
            }
        }
    }
}
