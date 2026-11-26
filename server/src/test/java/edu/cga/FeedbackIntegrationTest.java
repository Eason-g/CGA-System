package edu.cga;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import edu.cga.common.TimeService;
import edu.cga.domain.entity.AuditLog;
import edu.cga.domain.entity.FeedbackRound;
import edu.cga.domain.mapper.FeedbackRoundMapper;
import edu.cga.service.FeedbackRateLimiter;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import tools.jackson.databind.JsonNode;

/**
 * P1 1.3 服务对象评价：开启评价轮、公开查询与提交、重复提交、截止/关闭后拒绝、限流、
 * 助理本人只能看到平均分，以及考核时 service 维度默认取评价折算分、修改需填理由。
 * <p>
 * 用种子合同 CON_04（苏雨桐 · JOB_01 TA · 导师张明远 · 生效中）。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FeedbackIntegrationTest extends WriteApiTestBase {

    private static final String MENTOR = "zhang.mingyuan";
    private static final String MENTOR_MATH = "chen.shouren";
    private static final String DEPT_ADMIN = "li.wanqing";
    private static final String SCHOOL = "zhou.guoqiang";
    private static final String INTL = "wang.haitao";
    private static final String ASSISTANT = "su.yutong";
    private static final String CONTRACT = "CON_04";

    private static final String CLIENT_A = "client-aaaaaaaaaaaaaaaa";
    private static final String CLIENT_B = "client-bbbbbbbbbbbbbbbb";

    @Autowired
    private FeedbackRoundMapper roundMapper;
    @Autowired
    private FeedbackRateLimiter limiter;
    @Autowired
    private TimeService time;

    /** 第一轮的评价码（Order 1 开启） */
    private static String code;

    private JsonNode publicSend(HttpMethod method, String url, Object body, int expected) throws Exception {
        return publicSend(method, url, body, expected, "127.0.0.1");
    }

    private JsonNode publicSend(HttpMethod method, String url, Object body, int expected, String ip) throws Exception {
        var req = MockMvcRequestBuilders.request(method, url).with(r -> {
            r.setRemoteAddr(ip);
            return r;
        });
        if (body != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        var resp = mvc.perform(req).andReturn().getResponse();
        String text = resp.getContentAsString(StandardCharsets.UTF_8);
        assertThat(resp.getStatus()).as("%s %s → %s", method, url, text).isEqualTo(expected);
        return json.readTree(text);
    }

    private static Map<String, Object> feedback(int rating, String comment, String clientId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("rating", rating);
        m.put("comment", comment);
        m.put("clientId", clientId);
        return m;
    }

    @Test
    @Order(1)
    void openRoundPermissionsAndCode() throws Exception {
        // 权限反例：学生、国际生办 403；外院导师看不到该合同 404
        apiPost("/api/feedback-rounds", ASSISTANT, Map.of("contractId", CONTRACT), 403);
        apiPost("/api/feedback-rounds", INTL, Map.of("contractId", CONTRACT), 403);
        apiPost("/api/feedback-rounds", MENTOR_MATH, Map.of("contractId", CONTRACT), 404);
        apiPost("/api/feedback-rounds", null, Map.of("contractId", CONTRACT), 401);
        // 待签合同不能开启
        apiPost("/api/feedback-rounds", MENTOR, Map.of("contractId", "CON_03"), 400);
        // 截止日期不能早于今天
        apiPost("/api/feedback-rounds", MENTOR, Map.of("contractId", CONTRACT,
                "deadline", time.today().minusDays(1).toString()), 400);

        JsonNode round = apiPost("/api/feedback-rounds", MENTOR, Map.of("contractId", CONTRACT), 200).path("data");
        code = round.path("code").asString();
        assertThat(code).matches("^[ABCDEFGHJKMNPQRSTUVWXYZ23456789]{8}$");
        assertThat(round.path("status").asString()).isEqualTo("OPEN");
        assertThat(round.path("deadline").asString()).isEqualTo(time.today().plusDays(14).toString());
        // 同一合同同时只能有一轮进行中
        apiPost("/api/feedback-rounds", DEPT_ADMIN, Map.of("contractId", CONTRACT), 400);
    }

    @Test
    @Order(2)
    void publicQueryAndSubmit() throws Exception {
        JsonNode info = publicSend(HttpMethod.GET, "/api/public/feedback/" + code.toLowerCase(), null, 200).path("data");
        assertThat(info.path("jobTitle").asString()).isEqualTo("《机器学习导论（双语）》课程助教");
        assertThat(info.path("assistantName").asString()).isEqualTo("苏同学");
        assertThat(info.path("open").asBoolean()).isTrue();
        assertThat(info.toString()).doesNotContain("苏雨桐").doesNotContain("U_STU_SU");

        publicSend(HttpMethod.GET, "/api/public/feedback/ABCDEFGH", null, 404);
        publicSend(HttpMethod.GET, "/api/public/feedback/not-a-code", null, 404);

        publicSend(HttpMethod.POST, "/api/public/feedback/" + code, feedback(5, "答疑很耐心", CLIENT_A), 200);
        // 同一浏览器重复提交 → 409
        JsonNode dup = publicSend(HttpMethod.POST, "/api/public/feedback/" + code, feedback(1, "再来一次", CLIENT_A), 409);
        assertThat(dup.path("message").asString()).isEqualTo("您已提交过本轮评价，感谢参与");
        publicSend(HttpMethod.POST, "/api/public/feedback/" + code, feedback(4, "", CLIENT_B), 200);

        // 参数校验
        publicSend(HttpMethod.POST, "/api/public/feedback/" + code, feedback(0, "", "client-cccccccccccccccc"), 400);
        publicSend(HttpMethod.POST, "/api/public/feedback/" + code, feedback(6, "", "client-cccccccccccccccc"), 400);
        publicSend(HttpMethod.POST, "/api/public/feedback/" + code, feedback(3, "x".repeat(201), "client-cccccccccccccccc"), 400);
        publicSend(HttpMethod.POST, "/api/public/feedback/" + code, feedback(3, "", "short"), 400);
        publicSend(HttpMethod.POST, "/api/public/feedback/ABCDEFGH", feedback(3, "", "client-cccccccccccccccc"), 404);

        // 库里只存客户端 ID 的哈希
        String raw = mvc.perform(MockMvcRequestBuilders.get("/api/feedback-rounds").param("contractId", CONTRACT)
                        .header("Authorization", bearer(tokenOf(MENTOR))))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(raw).doesNotContain(CLIENT_A).doesNotContain("clientHash");
        JsonNode rounds = json.readTree(raw).path("data");
        assertThat(rounds.size()).isEqualTo(1);
        assertThat(rounds.get(0).path("count").asInt()).isEqualTo(2);
        assertThat(rounds.get(0).path("avgRating").decimalValue()).isEqualByComparingTo("4.5");
        assertThat(rounds.get(0).path("items").get(0).has("comment")).isTrue();
    }

    @Test
    @Order(3)
    void assistantOnlySeesAverage() throws Exception {
        JsonNode summary = send(HttpMethod.GET, "/api/contracts/" + CONTRACT + "/feedback-summary", ASSISTANT, null, 200)
                .path("data");
        assertThat(summary.path("count").asInt()).isEqualTo(2);
        assertThat(summary.path("avgRating").decimalValue()).isEqualByComparingTo("4.5");
        assertThat(summary.path("serviceScore").decimalValue()).isEqualByComparingTo("90");
        assertThat(summary.has("items")).isFalse();
        assertThat(summary.toString()).doesNotContain("答疑很耐心").doesNotContain(code);
        // 助理本人看不到评价轮列表（含评价码与单条评价），快照里也没有评价码
        send(HttpMethod.GET, "/api/feedback-rounds", ASSISTANT, null, 403);
        send(HttpMethod.GET, "/api/feedback-rounds", INTL, null, 403);
        String snap = mvc.perform(MockMvcRequestBuilders.get("/api/snapshot")
                        .header("Authorization", bearer(tokenOf(ASSISTANT))))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(snap).doesNotContain(code).doesNotContain("答疑很耐心");
        // 别人的合同看不到汇总
        send(HttpMethod.GET, "/api/contracts/" + CONTRACT + "/feedback-summary", "lin.zhiyuan", null, 404);
        // 外院导师看不到该合同的评价轮
        assertThat(send(HttpMethod.GET, "/api/feedback-rounds", MENTOR_MATH, null, 200).path("data").size()).isZero();
    }

    @Test
    @Order(4)
    void evaluationServiceDimensionDefaultsToFeedback() throws Exception {
        // 不传 service 分数 → 默认取 4.5 星 × 20 = 90 分
        JsonNode ev = apiPost("/api/evaluations", MENTOR, Map.of("contractId", CONTRACT,
                "dimensionScores", Map.of("mentor", 80)), 200).path("data");
        JsonNode service = dimension(ev, "service");
        assertThat(service.path("score").decimalValue()).isEqualByComparingTo("90");
        assertThat(service.path("feedbackCount").asInt()).isEqualTo(2);
        assertThat(ev.path("totalScore").decimalValue()).isEqualByComparingTo("83"); // 80×70% + 90×30%
        assertThat(logOf(ev.path("id").asString()).getOpinion()).contains("服务对象评价 2 条").contains("折算 90 分");

        // 传入与默认值相同的分数不需要理由
        apiPost("/api/evaluations", MENTOR, Map.of("contractId", CONTRACT,
                "dimensionScores", Map.of("mentor", 80, "service", 90)), 200);

        // 改动 service 分数但不写理由 → 400
        JsonNode r = apiPost("/api/evaluations", MENTOR, Map.of("contractId", CONTRACT,
                "dimensionScores", Map.of("mentor", 80, "service", 70)), 400);
        assertThat(r.path("message").asString()).contains("须填写修改理由");
        apiPost("/api/evaluations", MENTOR, Map.of("contractId", CONTRACT,
                "dimensionScores", Map.of("mentor", 80, "service", 70), "serviceOverrideReason", "  "), 400);

        // 写了理由 → 通过，理由进审计日志和考核明细
        JsonNode ok = apiPost("/api/evaluations", MENTOR, Map.of("contractId", CONTRACT,
                "dimensionScores", Map.of("mentor", 80, "service", 70),
                "serviceOverrideReason", "评价样本只有 2 份，结合课堂观察下调"), 200).path("data");
        assertThat(dimension(ok, "service").path("overrideReason").asString()).isEqualTo("评价样本只有 2 份，结合课堂观察下调");
        assertThat(logOf(ok.path("id").asString()).getOpinion()).contains("评价人改为 70 分，理由：评价样本只有 2 份，结合课堂观察下调");

        // 没有服务评价的合同：service 维度照旧（不需要理由）
        apiPost("/api/evaluations", MENTOR, Map.of("contractId", "CON_01",
                "dimensionScores", Map.of("mentor", 90, "service", 60)), 200);
    }

    @Test
    @Order(5)
    void submitAfterDeadlineIsRejected() throws Exception {
        FeedbackRound r = roundMapper.selectByCode(code);
        r.setDeadline(time.today().minusDays(1));
        roundMapper.updateById(r);

        JsonNode rej = publicSend(HttpMethod.POST, "/api/public/feedback/" + code,
                feedback(5, "", "client-dddddddddddddddd"), 400);
        assertThat(rej.path("message").asString()).contains("截止");
        JsonNode info = publicSend(HttpMethod.GET, "/api/public/feedback/" + code, null, 200).path("data");
        assertThat(info.path("open").asBoolean()).isFalse();
        assertThat(info.path("closedReason").asString()).contains("截止");
        JsonNode list = send(HttpMethod.GET, "/api/feedback-rounds?contractId=" + CONTRACT, MENTOR, null, 200).path("data");
        assertThat(list.get(0).path("status").asString()).isEqualTo("EXPIRED");
    }

    @Test
    @Order(6)
    void submitAfterCloseIsRejected() throws Exception {
        // 上一轮已截止，可以开启新一轮（院系管理员也可以开）
        JsonNode round = apiPost("/api/feedback-rounds", DEPT_ADMIN, Map.of("contractId", CONTRACT,
                "deadline", time.today().plusDays(7).toString()), 200).path("data");
        String code2 = round.path("code").asString();
        String roundId = round.path("id").asString();
        publicSend(HttpMethod.POST, "/api/public/feedback/" + code2, feedback(3, "一般", CLIENT_A), 200);

        // 关闭权限反例：学生 403、外院导师 404
        apiPost("/api/feedback-rounds/" + roundId + "/close", ASSISTANT, null, 403);
        apiPost("/api/feedback-rounds/" + roundId + "/close", MENTOR_MATH, null, 404);
        long logs = auditCount();
        JsonNode closed = apiPost("/api/feedback-rounds/" + roundId + "/close", MENTOR, null, 200).path("data");
        assertThat(closed.path("status").asString()).isEqualTo("CLOSED");
        assertThat(auditCount()).isEqualTo(logs + 1);
        apiPost("/api/feedback-rounds/" + roundId + "/close", MENTOR, null, 400);

        JsonNode rej = publicSend(HttpMethod.POST, "/api/public/feedback/" + code2, feedback(5, "", CLIENT_B), 400);
        assertThat(rej.path("message").asString()).isEqualTo("本轮评价已关闭");
        // 两轮合计 3 条评价
        JsonNode summary = send(HttpMethod.GET, "/api/contracts/" + CONTRACT + "/feedback-summary", SCHOOL, null, 200)
                .path("data");
        assertThat(summary.path("count").asInt()).isEqualTo(3);
        assertThat(summary.path("avgRating").decimalValue()).isEqualByComparingTo("4.00");
    }

    @Test
    @Order(7)
    void rateLimitIsPerIpAndCode() throws Exception {
        String ip = "10.9.9.9";
        String url = "/api/public/feedback/" + code;
        for (int i = 0; i < limiter.queryLimit(); i++) {
            publicSend(HttpMethod.GET, url, null, 200, ip);
        }
        JsonNode limited = publicSend(HttpMethod.GET, url, null, 429, ip);
        assertThat(limited.path("code").asInt()).isEqualTo(429);
        // 其他 IP 不受影响（同一出口之外的人照常使用）
        publicSend(HttpMethod.GET, url, null, 200, "10.9.9.10");

        // 提交单独计数：评分非法的请求也计入（400），超出后 429
        for (int i = 0; i < limiter.submitLimit(); i++) {
            publicSend(HttpMethod.POST, url, feedback(0, "", "client-eeeeeeeeeeeeeeee"), 400, ip);
        }
        publicSend(HttpMethod.POST, url, feedback(0, "", "client-eeeeeeeeeeeeeeee"), 429, ip);
        // 阈值能容纳一个班（几十人）在同一出口下集中评价
        assertThat(limiter.submitLimit()).isGreaterThanOrEqualTo(100);
        assertThat(limiter.queryLimit()).isGreaterThanOrEqualTo(200);
    }

    private static JsonNode dimension(JsonNode ev, String key) {
        for (JsonNode d : ev.path("dimensions")) {
            if (key.equals(d.path("key").asString())) {
                return d;
            }
        }
        throw new AssertionError("考核明细里没有维度 " + key);
    }

    /** 某考核单的审计日志 */
    private AuditLog logOf(String targetId) {
        return auditLogMapper.selectOne(new QueryWrapper<AuditLog>().eq("target_id", targetId));
    }
}
