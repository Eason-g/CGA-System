package edu.cga;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import edu.cga.domain.entity.Budget;
import edu.cga.domain.entity.Contract;
import edu.cga.domain.entity.Payroll;
import edu.cga.domain.mapper.NotificationMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

/**
 * 完整主流程：导师建岗 → 院系初审 → 国际生办会签（TA 面向国际生）→ 发布 → 学生申请 → 安排面试 → 录用
 * → 学生签约 → 填报工时 → 导师审核 → 院系核算薪酬 → 校级发放。
 * 断言经费 frozen / used 的变化与审计日志条数。
 */
class MainFlowIntegrationTest extends WriteApiTestBase {

    private static final String MENTOR = "zhang.mingyuan";
    private static final String DEPT_ADMIN = "li.wanqing";
    private static final String INTL = "wang.haitao";
    private static final String SCHOOL = "zhou.guoqiang";
    private static final String STUDENT = "lin.zhiyuan";

    @Autowired
    private NotificationMapper notificationMapper;

    @Test
    void fullFlowFromJobCreationToPayout() throws Exception {
        Budget before = budget("B_CS_TA");
        long auditBefore = auditCount();

        // 1. 导师建岗：1 人 × ¥45 × 6h × 16 周 = ¥4,320，冻结到经费
        Map<String, Object> draft = new LinkedHashMap<>();
        draft.put("type", "TA");
        draft.put("title", "  《编译原理（双语）》课程助教  ");
        draft.put("deptId", "DEPT_CS");
        draft.put("budgetId", "B_CS_TA");
        draft.put("quota", 1);
        draft.put("hourlyRate", 45);
        draft.put("weeklyHours", 6);
        draft.put("weeks", 16);
        draft.put("allowInternational", true);
        draft.put("deadline", "2026-12-28");
        draft.put("skills", List.of("编译原理", "英语授课"));
        draft.put("description", "协助批改实验作业");
        // P1：TA 岗位的必填差异化字段（jobTypeSchemas）
        draft.put("extraFields", Map.of("courseCode", "CS3150", "courseName", "编译原理（双语）",
                "semester", "2026-2027 春季学期", "minLangScore", 6.5));
        draft.put("operator", Map.of("id", "U_SCHOOL_ADMIN")); // 前端会多传 operator，后端忽略
        JsonNode created = apiPost("/api/jobs", MENTOR, draft, 200);
        assertThat(created.path("message").asString()).isEqualTo("岗位已提交并进入审批流");
        JsonNode job = created.path("data");
        String jobId = job.path("id").asString();
        assertThat(job.path("title").asString()).isEqualTo("《编译原理（双语）》课程助教");
        assertThat(job.path("mentorId").asString()).isEqualTo("U_MENTOR");
        assertThat(job.path("status").asString()).isEqualTo("PENDING_DEPT_REVIEW");
        assertThat(job.path("currentStepIndex").asInt()).isEqualTo(1);
        assertThat(job.path("workflowSteps").toString()).contains("INTL_REVIEW");
        assertThat(job.path("reservedAmount").decimalValue()).isEqualByComparingTo("4320");

        Budget afterCreate = budget("B_CS_TA");
        assertThat(afterCreate.getFrozenAmount()).isEqualByComparingTo(before.getFrozenAmount().add(new BigDecimal("4320")));
        assertThat(afterCreate.getUsedAmount()).isEqualByComparingTo(before.getUsedAmount());

        // 2. 导师本人不能审批（当前节点属于院系管理员）
        apiPost("/api/jobs/" + jobId + "/review", MENTOR, Map.of("decision", "APPROVE"), 403);
        // 国际生办不能越过院系初审
        apiPost("/api/jobs/" + jobId + "/review", INTL, Map.of("decision", "APPROVE"), 403);

        // 3. 院系初审 → 国际生办会签
        JsonNode r1 = apiPost("/api/jobs/" + jobId + "/review", DEPT_ADMIN, Map.of("decision", "APPROVE", "opinion", "名额经费核验通过"), 200);
        assertThat(r1.path("data").path("status").asString()).isEqualTo("PENDING_INTL_REVIEW");
        assertThat(r1.path("message").asString()).isEqualTo("审批通过并已流转");
        // 院系管理员不能再审国际生办节点
        apiPost("/api/jobs/" + jobId + "/review", DEPT_ADMIN, Map.of("decision", "APPROVE"), 403);
        JsonNode r2 = apiPost("/api/jobs/" + jobId + "/review", INTL, Map.of("decision", "APPROVE"), 200);
        assertThat(r2.path("data").path("status").asString()).isEqualTo("PUBLISHED");
        // 已发布的岗位不能再审
        apiPost("/api/jobs/" + jobId + "/review", SCHOOL, Map.of("decision", "APPROVE"), 400);

        // 4. 学生申请（林致远已有 12h 合同，本岗 6h，合计 18h ≤ 20h）
        JsonNode applied = apiPost("/api/applications", STUDENT, Map.of("jobId", jobId, "statement", "熟悉编译原理"), 200);
        assertThat(applied.path("message").asString()).isEqualTo("投递成功，等待导师筛选");
        JsonNode app = applied.path("data");
        String appId = app.path("id").asString();
        assertThat(app.path("status").asString()).isEqualTo("SUBMITTED");
        assertThat(app.path("studentId").asString()).isEqualTo("U_CN_STU");
        assertThat(app.path("complianceCheckResult").path("projectedHours").asInt()).isEqualTo(18);
        // 重复投递被拦截
        JsonNode dup = apiPost("/api/applications", STUDENT, Map.of("jobId", jobId), 400);
        assertThat(dup.path("message").asString()).isEqualTo("您已申请过该岗位，请勿重复投递");
        assertThat(dup.path("data").path("alreadyApplied").asBoolean()).isTrue();

        // 5. 安排面试
        JsonNode iv = apiPost("/api/applications/" + appId + "/interview", MENTOR,
                Map.of("time", "2026-11-24 14:00", "location", "信息楼 412", "note", "带作品"), 200);
        assertThat(iv.path("data").path("status").asString()).isEqualTo("INTERVIEW_SCHEDULED");
        assertThat(iv.path("data").path("interviewInfo").path("location").asString()).isEqualTo("信息楼 412");

        // 6. 录用 → 生成待签合同
        JsonNode hired = apiPost("/api/applications/" + appId + "/decision", MENTOR,
                Map.of("score", 92, "decision", "HIRE", "comment", "同意录用"), 200);
        assertThat(hired.path("message").asString()).isEqualTo("已录用并自动下发电子聘用协议与通知");
        assertThat(hired.path("data").path("status").asString()).isEqualTo("HIRED");
        assertThat(hired.path("data").path("score").asInt()).isEqualTo(92);
        assertThat(jobMapper.selectById(jobId).getHiredCount()).isEqualTo(1);
        Contract con = contractMapper.selectOne(new QueryWrapper<Contract>().eq("application_id", appId));
        assertThat(con.getStatus()).isEqualTo("PENDING_SIGN");
        assertThat(con.getWeeklyHours()).isEqualTo(6);
        // 聘期从演示日期（2026-11-20，周五）的下周一开始，持续 16 周
        assertThat(con.getStartDate().toString()).isEqualTo("2026-11-23");
        assertThat(con.getEndDate().toString()).isEqualTo("2027-03-15");

        // 7. 学生签约
        JsonNode signed = apiPost("/api/contracts/" + con.getId() + "/sign", STUDENT,
                Map.of("signatureDataUrl", "data:image/png;base64,iVBORw0KGgo=", "complianceAgreed", false), 200);
        assertThat(signed.path("data").path("status").asString()).isEqualTo("ACTIVE");
        assertThat(signed.path("data").path("signatureText").asString()).isEqualTo("林致远");
        apiPost("/api/contracts/" + con.getId() + "/sign", STUDENT,
                Map.of("signatureDataUrl", "data:image/png;base64,iVBORw0KGgo="), 400);

        // 8. 填报工时：第 12 周已报 11h（TS_03），本次 6h → 17h ≤ 20h
        Map<String, Object> ts = Map.of("contractId", con.getId(), "weekNo", 12, "hours", 6,
                "workLog", "批改第 3 次实验作业");
        JsonNode submitted = apiPost("/api/timesheets", STUDENT, ts, 200);
        assertThat(submitted.path("message").asString()).isEqualTo("工时已提交，等待导师审核");
        String tsId = submitted.path("data").path("id").asString();
        assertThat(submitted.path("data").path("month").asString()).isEqualTo("2026-11");
        // 再报 4h 就超过 20h，被拦截
        JsonNode over = apiPost("/api/timesheets", STUDENT,
                Map.of("contractId", con.getId(), "weekNo", 12, "hours", 4, "workLog", "补报"), 403);
        assertThat(over.path("message").asString()).contains("第 12 周跨岗累计工时将达 21h");
        // 不能填报未来周次
        apiPost("/api/timesheets", STUDENT, Map.of("contractId", con.getId(), "weekNo", 13, "hours", 1, "workLog", "x"), 400);

        // 9. 导师审核通过
        JsonNode reviewed = apiPost("/api/timesheets/" + tsId + "/review", MENTOR, Map.of("decision", "APPROVE"), 200);
        assertThat(reviewed.path("data").path("status").asString()).isEqualTo("APPROVED");
        assertThat(reviewed.path("data").path("mentorComment").asString()).isEqualTo("工时核实无误，同意通过");
        apiPost("/api/timesheets/" + tsId + "/review", MENTOR, Map.of("decision", "APPROVE"), 400);

        // 10. 院系管理员核算本院系 2026-11 薪酬
        JsonNode gen = apiPost("/api/payrolls/generate", DEPT_ADMIN, Map.of("month", "2026-11"), 200);
        assertThat(gen.path("data").path("blocked").size()).isZero();
        int count = gen.path("data").path("count").asInt();
        assertThat(count).isGreaterThanOrEqualTo(1);
        assertThat(gen.path("message").asString())
                .isEqualTo("已完成 2026-11 薪酬自动核算（更新 " + count + " 笔，预算拦截 0 笔）");
        Payroll pay = payrollMapper.selectByContractAndMonth(con.getId(), "2026-11");
        assertThat(pay.getFinanceStatus()).isEqualTo("PENDING_PAY");
        assertThat(pay.getAmount()).isEqualByComparingTo("270"); // 6h × ¥45 × 1.0
        // 数学学院的薪酬不在李婉清的核算范围内
        assertThat(payrollMapper.selectById("PAY_06").getCalculatedAt().toString()).startsWith("2026-11-19");

        // 11. 校级管理员发放：used +270，frozen −270（本岗位预留足够）
        Budget beforePay = budget("B_CS_TA");
        JsonNode paid = apiPost("/api/payrolls/" + pay.getId() + "/payout", SCHOOL, null, 200);
        assertThat(paid.path("data").path("financeStatus").asString()).isEqualTo("PAID");
        assertThat(paid.path("data").path("voucherNo").asString()).startsWith("CW-202611-");
        assertThat(paid.path("message").asString()).startsWith("财务打款成功（凭证 CW-202611-");
        Budget afterPay = budget("B_CS_TA");
        assertThat(afterPay.getUsedAmount()).isEqualByComparingTo(beforePay.getUsedAmount().add(new BigDecimal("270")));
        assertThat(afterPay.getFrozenAmount()).isEqualByComparingTo(beforePay.getFrozenAmount().subtract(new BigDecimal("270")));
        apiPost("/api/payrolls/" + pay.getId() + "/payout", SCHOOL, null, 400);

        // 审计日志：建岗 1 + 审批 2 + 申请 1 + 面试 1 + 录用 1 + 签约 1 + 填报 1 + 审核 1 + 核算 1 + 发放 1 = 11（被拦截的操作不留日志）
        assertThat(auditCount()).isEqualTo(auditBefore + 11);

        // 学生收到面试、录用、薪酬发放三封站内信
        long notifs = notificationMapper.selectCount(new QueryWrapper<edu.cga.domain.entity.Notification>()
                .eq("user_id", "U_CN_STU"));
        assertThat(notifs).isEqualTo(1 + 3); // 种子 1 封 + 本流程 3 封

        // 学生快照里能看到自己的新合同、工时、薪酬，以及跨岗总负荷
        JsonNode snap = snapshotAs(STUDENT);
        JsonNode self = null;
        for (JsonNode u : snap.path("users")) {
            if ("U_CN_STU".equals(u.path("id").asString())) {
                self = u;
            }
        }
        assertThat(self).isNotNull();
        assertThat(self.path("hoursSummary").path("allocated").asInt()).isEqualTo(18);
        assertThat(self.path("hoursSummary").path("max").asInt()).isEqualTo(20);
    }
}
