package edu.cga;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import edu.cga.common.TimeService;
import edu.cga.domain.entity.AuditLog;
import edu.cga.domain.entity.Evaluation;
import edu.cga.domain.mapper.EvaluationMapper;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import tools.jackson.databind.JsonNode;

/**
 * P2 C.1 评教联动：GET /api/integration/teaching-eval 与考核提交时写入 courseEvalSync。
 * <p>
 * 种子合同：CON_04（苏雨桐 · JOB_01 TA · CS3042 · 2026-2027 秋季学期 · 导师张明远）在模拟评教数据中有结果；
 * CON_05（Elena · JOB_08 TA · MATH1012）故意没有评教数据；CON_01 为 RA 合同。
 * 模拟数据 CS3042：48 份，助教题目 4.78 / 4.66 / 4.69 → 均分 4.71，折算 94.2 分，满意度 95.8%。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TeachingEvalIntegrationTest extends WriteApiTestBase {

    private static final String MENTOR = "zhang.mingyuan";
    private static final String MENTOR_MATH = "chen.shouren";
    private static final String DEPT_ADMIN = "li.wanqing";
    private static final String SCHOOL = "zhou.guoqiang";
    private static final String URL = "/api/integration/teaching-eval?contractId=";

    @Autowired
    private EvaluationMapper evaluationMapper;
    @Autowired
    private TimeService time;

    private JsonNode get(String url, String account, int expected) throws Exception {
        return send(HttpMethod.GET, url, account, null, expected);
    }

    @Test
    @Order(1)
    void syncReturnsMockEvaluationForTaContract() throws Exception {
        JsonNode r = get(URL + "CON_04", MENTOR, 200);
        JsonNode d = r.path("data");
        assertThat(d.path("available").asBoolean()).isTrue();
        assertThat(d.path("source").asString()).isEqualTo("模拟评教系统");
        assertThat(d.path("courseCode").asString()).isEqualTo("CS3042");
        assertThat(d.path("semester").asString()).isEqualTo("2026-2027 秋季学期");
        assertThat(d.path("responseCount").asInt()).isEqualTo(48);
        assertThat(d.path("enrolledCount").asInt()).isEqualTo(52);
        assertThat(d.path("avgRating").decimalValue()).isEqualByComparingTo("4.71");
        assertThat(d.path("satisfactionRate").decimalValue()).isEqualByComparingTo("95.8");
        assertThat(d.path("serviceScore").decimalValue()).isEqualByComparingTo("94.2");
        assertThat(d.path("questions").size()).isEqualTo(3);
        assertThat(r.path("message").asString()).contains("模拟评教系统");

        // 院系管理员、校级管理员同样可查
        get(URL + "CON_04", DEPT_ADMIN, 200);
        get(URL + "CON_04", SCHOOL, 200);
    }

    @Test
    @Order(2)
    void courseWithoutDataIsReportedHonestly() throws Exception {
        JsonNode r = get(URL + "CON_05", SCHOOL, 200);
        JsonNode d = r.path("data");
        assertThat(d.path("available").asBoolean()).isFalse();
        assertThat(d.path("courseCode").asString()).isEqualTo("MATH1012");
        assertThat(d.path("message").asString()).isEqualTo("该课程暂无评教数据");
        assertThat(r.path("message").asString()).isEqualTo("该课程暂无评教数据");
        assertThat(d.has("responseCount")).isFalse();
        // 数学学院导师查自己的岗位也一样
        assertThat(get(URL + "CON_05", MENTOR_MATH, 200).path("data").path("available").asBoolean()).isFalse();
    }

    @Test
    @Order(3)
    void nonTaContractAndPermissions() throws Exception {
        // RA 合同 400
        JsonNode bad = get(URL + "CON_01", MENTOR, 400);
        assertThat(bad.path("message").asString()).contains("仅助教（TA）");
        get("/api/integration/teaching-eval", MENTOR, 400);
        // 学生 403（包括助理本人）
        get(URL + "CON_04", "su.yutong", 403);
        get(URL + "CON_04", "alex.chen", 403);
        get(URL + "CON_04", "wang.haitao", 403);
        // 外院导师看不到该合同 404
        get(URL + "CON_04", MENTOR_MATH, 404);
        get(URL + "CON_04", "zhao.minghua", 404);
        get(URL + "NOT_EXIST", MENTOR, 404);
        get(URL + "CON_04", null, 401);
    }

    @Test
    @Order(4)
    void submitWritesCourseEvalSyncFromGatewayNotFromClient() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("contractId", "CON_04");
        body.put("dimensionScores", Map.of("mentor", 90));
        body.put("syncTeachingEval", true);
        // 前端伪造的数字不被采信
        body.put("courseEvalSync", Map.of("responseCount", 999, "satisfactionRate", 100, "source", "伪造"));
        JsonNode ev = apiPost("/api/evaluations", MENTOR, body, 200).path("data");

        JsonNode sync = ev.path("courseEvalSync");
        assertThat(sync.path("source").asString()).isEqualTo("模拟评教系统");
        assertThat(sync.path("sourceCode").asString()).isEqualTo("MOCK");
        assertThat(sync.path("courseCode").asString()).isEqualTo("CS3042");
        assertThat(sync.path("semester").asString()).isEqualTo("2026-2027 秋季学期");
        assertThat(sync.path("responseCount").asInt()).isEqualTo(48);
        assertThat(sync.path("avgRating").decimalValue()).isEqualByComparingTo("4.71");
        assertThat(sync.path("satisfactionRate").decimalValue()).isEqualByComparingTo("95.8");
        assertThat(sync.path("appliedAsDefault").asBoolean()).isTrue();
        assertThat(sync.path("syncedAt").asString()).startsWith(time.today().toString());

        // service 维度默认取 4.71 × 20 = 94.2；总分 90×70% + 94.2×30% = 91.26 → 91.3
        JsonNode service = dimension(ev, "service");
        assertThat(service.path("score").decimalValue()).isEqualByComparingTo("94.2");
        assertThat(service.path("defaultSource").asString()).isEqualTo("TEACHING_EVAL");
        assertThat(service.path("teachingEvalScore").decimalValue()).isEqualByComparingTo("94.2");
        assertThat(ev.path("totalScore").decimalValue()).isEqualByComparingTo("91.3");

        // 落库内容同样来自评教系统
        Evaluation saved = evaluationMapper.selectById(ev.path("id").asString());
        assertThat(saved.getCourseEvalSync()).containsEntry("source", "模拟评教系统");
        assertThat(new BigDecimal(saved.getCourseEvalSync().get("responseCount").toString())).isEqualByComparingTo("48");
        assertThat(logOf(ev.path("id").asString()).getOpinion())
                .contains("模拟评教系统课程评教 48 份").contains("折算 94.2 分").contains("默认取课程评教折算分");

        // 未同步时不写 courseEvalSync
        JsonNode plain = apiPost("/api/evaluations", MENTOR, Map.of("contractId", "CON_04",
                "dimensionScores", Map.of("mentor", 90, "service", 88)), 200).path("data");
        assertThat(plain.path("courseEvalSync").isNull() || plain.path("courseEvalSync").isMissingNode()).isTrue();
    }

    @Test
    @Order(5)
    void overrideNeedsReasonAndNoDataCannotSync() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("contractId", "CON_04");
        body.put("dimensionScores", Map.of("mentor", 90, "service", 80));
        body.put("syncTeachingEval", true);
        JsonNode r = apiPost("/api/evaluations", MENTOR, body, 400);
        assertThat(r.path("message").asString()).contains("课程评教折算分").contains("须填写修改理由");

        body.put("serviceOverrideReason", "评教回收率偏低，结合听课记录下调");
        JsonNode ok = apiPost("/api/evaluations", MENTOR, body, 200).path("data");
        assertThat(dimension(ok, "service").path("overrideReason").asString()).isEqualTo("评教回收率偏低，结合听课记录下调");

        // 没有评教数据的课程不能同步
        JsonNode none = apiPost("/api/evaluations", SCHOOL, Map.of("contractId", "CON_05",
                "dimensionScores", Map.of("mentor", 90), "syncTeachingEval", true), 400);
        assertThat(none.path("message").asString()).contains("该课程暂无评教数据");
        // RA 合同不能同步
        apiPost("/api/evaluations", MENTOR, Map.of("contractId", "CON_01",
                "dimensionScores", Map.of("mentor", 90), "syncTeachingEval", true), 400);
        // 默认值来源取值无效
        apiPost("/api/evaluations", MENTOR, Map.of("contractId", "CON_04",
                "dimensionScores", Map.of("mentor", 90), "serviceDefaultSource", "OTHER"), 400);
        // 没有服务对象评价时不能选 FEEDBACK
        apiPost("/api/evaluations", MENTOR, Map.of("contractId", "CON_04",
                "dimensionScores", Map.of("mentor", 90), "serviceDefaultSource", "FEEDBACK"), 400);
    }

    @Test
    @Order(6)
    void feedbackAndTeachingEvalBothShownMentorChoosesDefault() throws Exception {
        // 开启服务评价轮并提交两条评价：5 星、3 星 → 平均 4 星，折算 80 分
        String code = apiPost("/api/feedback-rounds", MENTOR, Map.of("contractId", "CON_04"), 200)
                .path("data").path("code").asString();
        apiPost("/api/public/feedback/" + code, null,
                Map.of("rating", 5, "comment", "", "clientId", "teaching-eval-client-a"), 200);
        apiPost("/api/public/feedback/" + code, null,
                Map.of("rating", 3, "comment", "", "clientId", "teaching-eval-client-b"), 200);

        // 选课程评教为默认值
        JsonNode a = apiPost("/api/evaluations", MENTOR, Map.of("contractId", "CON_04",
                "dimensionScores", Map.of("mentor", 90), "syncTeachingEval", true,
                "serviceDefaultSource", "TEACHING_EVAL"), 200).path("data");
        JsonNode sa = dimension(a, "service");
        assertThat(sa.path("score").decimalValue()).isEqualByComparingTo("94.2");
        assertThat(sa.path("feedbackScore").decimalValue()).isEqualByComparingTo("80");
        assertThat(sa.path("teachingEvalScore").decimalValue()).isEqualByComparingTo("94.2");
        assertThat(a.path("courseEvalSync").path("appliedAsDefault").asBoolean()).isTrue();

        // 选服务对象评价为默认值：评教结果仍记录在 courseEvalSync，但未采用
        JsonNode b = apiPost("/api/evaluations", MENTOR, Map.of("contractId", "CON_04",
                "dimensionScores", Map.of("mentor", 90), "syncTeachingEval", true,
                "serviceDefaultSource", "FEEDBACK"), 200).path("data");
        JsonNode sb = dimension(b, "service");
        assertThat(sb.path("score").decimalValue()).isEqualByComparingTo("80");
        assertThat(sb.path("defaultSource").asString()).isEqualTo("FEEDBACK");
        assertThat(b.path("courseEvalSync").path("responseCount").asInt()).isEqualTo(48);
        assertThat(b.path("courseEvalSync").path("appliedAsDefault").asBoolean()).isFalse();

        // 选了服务对象评价却填评教分，属于改分，必须写理由
        apiPost("/api/evaluations", MENTOR, Map.of("contractId", "CON_04",
                "dimensionScores", Map.of("mentor", 90, "service", 94.2), "syncTeachingEval", true,
                "serviceDefaultSource", "FEEDBACK"), 400);

        // 不指定来源时沿用 P1：有服务对象评价就取它
        JsonNode c = apiPost("/api/evaluations", MENTOR, Map.of("contractId", "CON_04",
                "dimensionScores", Map.of("mentor", 90), "syncTeachingEval", true), 200).path("data");
        assertThat(dimension(c, "service").path("score").decimalValue()).isEqualByComparingTo("80");
    }

    private static JsonNode dimension(JsonNode ev, String key) {
        for (JsonNode d : ev.path("dimensions")) {
            if (key.equals(d.path("key").asString())) {
                return d;
            }
        }
        throw new AssertionError("考核单缺少维度 " + key);
    }

    private AuditLog logOf(String evaluationId) {
        List<AuditLog> logs = auditLogMapper.selectList(new QueryWrapper<AuditLog>()
                .eq("target_type", "EVALUATION").eq("target_id", evaluationId));
        assertThat(logs).hasSize(1);
        return logs.get(0);
    }
}
