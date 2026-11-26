package edu.cga;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.http.HttpMethod;
import tools.jackson.databind.JsonNode;

/**
 * P2 C.2 智能推荐：GET /api/recommendations 与配置中心的推荐权重。
 * <p>
 * 以 alex.chen（国际生 · 计算机学院 · 已占 12h/20h）为例：已发布岗位 JOB_01/02/03/04/05/08/09/10/11，
 * 其中 JOB_10、JOB_11 已申请（不推荐），JOB_04、JOB_05、JOB_09 不面向国际生（硬性条件不过 → 不可申请）。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RecommendationIntegrationTest extends IntegrationTestBase {

    private static final String URL = "/api/recommendations";
    private static final String ALEX = "alex.chen";
    private static final String SCHOOL = "zhou.guoqiang";

    private JsonNode recommend(String account) throws Exception {
        return send(HttpMethod.GET, URL, account, null, 200).path("data");
    }

    private static List<String> ids(JsonNode items, boolean applicable) {
        List<String> out = new ArrayList<>();
        for (JsonNode it : items) {
            if (it.path("applicable").asBoolean() == applicable) {
                out.add(it.path("jobId").asString());
            }
        }
        return out;
    }

    private static JsonNode item(JsonNode data, String jobId) {
        for (JsonNode it : data.path("items")) {
            if (jobId.equals(it.path("jobId").asString())) {
                return it;
            }
        }
        throw new AssertionError("推荐列表缺少岗位 " + jobId);
    }

    private void saveWeights(Map<String, Object> weights, int expected) throws Exception {
        apiPut("/api/configs", SCHOOL, Map.of("globalRules", Map.of("recommendWeights", weights)), expected);
    }

    private static Map<String, Object> only(String key) {
        Map<String, Object> w = new LinkedHashMap<>();
        for (String k : List.of("skill", "hours", "language", "dept", "deadline")) {
            w.put(k, k.equals(key) ? 100 : 0);
        }
        return w;
    }

    @Test
    @Order(1)
    void onlyPublishedAndNotAppliedJobs() throws Exception {
        JsonNode data = recommend(ALEX);
        List<String> all = new ArrayList<>();
        data.path("items").forEach(it -> all.add(it.path("jobId").asString()));
        assertThat(all).containsExactlyInAnyOrder("JOB_01", "JOB_02", "JOB_03", "JOB_04", "JOB_05", "JOB_08", "JOB_09");
        // 待审岗位 JOB_06/07 与已申请的 JOB_10/11 不出现
        assertThat(all).doesNotContain("JOB_06", "JOB_07", "JOB_10", "JOB_11");
        assertThat(data.path("currentHours").asInt()).isEqualTo(12);
        assertThat(data.path("maxHours").asInt()).isEqualTo(20);
        // 种子配置里的默认权重
        JsonNode w = data.path("weights");
        assertThat(w.path("skill").asInt()).isEqualTo(40);
        assertThat(w.path("hours").asInt()).isEqualTo(25);
        assertThat(w.path("language").asInt()).isEqualTo(15);
        assertThat(w.path("dept").asInt()).isEqualTo(10);
        assertThat(w.path("deadline").asInt()).isEqualTo(10);

        // 国内生 lin.zhiyuan：已录用的 JOB_04 不出现
        List<String> lin = new ArrayList<>();
        recommend("lin.zhiyuan").path("items").forEach(it -> lin.add(it.path("jobId").asString()));
        assertThat(lin).doesNotContain("JOB_04", "JOB_06", "JOB_07").contains("JOB_01", "JOB_09");
    }

    @Test
    @Order(2)
    void hardFailuresAreNotApplicableAndNotOnTop() throws Exception {
        JsonNode data = recommend(ALEX);
        JsonNode items = data.path("items");
        List<String> applicable = ids(items, true);
        List<String> blocked = ids(items, false);
        assertThat(blocked).containsExactlyInAnyOrder("JOB_04", "JOB_05", "JOB_09");
        assertThat(applicable).containsExactlyInAnyOrder("JOB_01", "JOB_02", "JOB_03", "JOB_08");
        // 不可申请的排在所有可申请岗位之后，且不进前 3
        for (int i = 0; i < items.size(); i++) {
            boolean ok = items.get(i).path("applicable").asBoolean();
            assertThat(ok).as("第 %d 项", i).isEqualTo(i < applicable.size());
        }
        List<String> top = new ArrayList<>();
        data.path("topJobIds").forEach(n -> top.add(n.asString()));
        assertThat(top).hasSize(3).doesNotContainAnyElementsOf(blocked);
        assertThat(top).containsExactlyElementsOf(applicable.subList(0, 3));

        JsonNode job04 = item(data, "JOB_04");
        assertThat(job04.path("score").asInt()).isZero();
        assertThat(job04.path("blockers").get(0).asString()).contains("岗位国际生开放资格校验未通过");
        assertThat(job04.path("reasons").size()).isZero();

        // 可特批项（JOB_02 工时超限、JOB_03 语言不足）仍可申请，但标出阻碍
        JsonNode job02 = item(data, "JOB_02");
        assertThat(job02.path("needsSpecialApproval").asBoolean()).isTrue();
        assertThat(job02.path("blockers").get(0).asString()).contains("24h > 20h");
        JsonNode job03 = item(data, "JOB_03");
        assertThat(job03.path("needsSpecialApproval").asBoolean()).isTrue();
        assertThat(job03.path("blockers").toString()).contains("语言要求未满足");
        JsonNode job01 = item(data, "JOB_01");
        assertThat(job01.path("needsSpecialApproval").asBoolean()).isFalse();
        assertThat(job01.path("reasons").toString()).contains("技能匹配 3/3");
    }

    @Test
    @Order(3)
    void scoreFollowsWeightsAndOrderChangesWithWeights() throws Exception {
        JsonNode data = recommend(ALEX);
        // 分数 = Σ 权重 × 取值 / Σ权重 × 100，且可申请岗位按分数降序
        int prev = Integer.MAX_VALUE;
        for (JsonNode it : data.path("items")) {
            if (!it.path("applicable").asBoolean()) {
                continue;
            }
            double sum = 0;
            for (JsonNode f : it.path("factors")) {
                double expected = f.path("weight").asDouble() * f.path("value").asDouble() / 100 * 100;
                // value 只保留两位小数，points 按未舍入的取值计算，允许少量误差
                assertThat(f.path("points").asDouble()).isCloseTo(expected, org.assertj.core.data.Offset.offset(0.25));
                sum += f.path("weight").asDouble() * f.path("value").asDouble();
            }
            assertThat(it.path("score").asInt()).isEqualTo((int) Math.round(sum)); // 默认权重之和为 100
            assertThat(it.path("score").asInt()).isLessThanOrEqualTo(prev);
            prev = it.path("score").asInt();
        }
        // JOB_01：技能 3/3=1、工时 20/20h 接近上限 0.6、语言满足 1、同院系 1、20 天后截止 40/53
        double job01 = 40 + 25 * 0.6 + 15 + 10 + 10 * (40.0 / 53);
        assertThat(item(data, "JOB_01").path("score").asInt()).isEqualTo((int) Math.round(job01));

        // 只看截止临近：JOB_01(20 天) > JOB_08(24 天) > JOB_02(25 天) > JOB_03(28 天)
        saveWeights(only("deadline"), 200);
        assertThat(ids(recommend(ALEX).path("items"), true)).containsExactly("JOB_01", "JOB_08", "JOB_02", "JOB_03");

        // 只看同院系：数学学院的 JOB_08 落到最后，得 0 分
        saveWeights(only("dept"), 200);
        JsonNode byDept = recommend(ALEX);
        List<String> deptOrder = ids(byDept.path("items"), true);
        assertThat(deptOrder.get(deptOrder.size() - 1)).isEqualTo("JOB_08");
        assertThat(item(byDept, "JOB_08").path("score").asInt()).isZero();
        assertThat(item(byDept, "JOB_02").path("score").asInt()).isEqualTo(100);

        // 只看技能：JOB_01(3/3) 第一，JOB_08(1/3) 最后；权重之和不是 100 也按比例归一
        Map<String, Object> skill = only("skill");
        skill.put("skill", 7);
        saveWeights(skill, 200);
        JsonNode bySkill = recommend(ALEX);
        List<String> skillOrder = ids(bySkill.path("items"), true);
        assertThat(skillOrder.get(0)).isEqualTo("JOB_01");
        assertThat(skillOrder.get(skillOrder.size() - 1)).isEqualTo("JOB_08");
        assertThat(item(bySkill, "JOB_01").path("score").asInt()).isEqualTo(100);
        assertThat(item(bySkill, "JOB_08").path("score").asInt()).isEqualTo(33);
        assertThat(bySkill.path("weights").path("skill").asInt()).isEqualTo(7);

        // 快照里的配置同步更新（配置中心读取）
        JsonNode cfg = snapshotAs(SCHOOL).path("configs").path("globalRules").path("recommendWeights");
        assertThat(cfg.path("skill").asInt()).isEqualTo(7);
        assertThat(cfg.path("dept").asInt()).isZero();
    }

    @Test
    @Order(4)
    void invalidWeightsRejected() throws Exception {
        Map<String, Object> negative = only("skill");
        negative.put("hours", -5);
        JsonNode r = apiPut("/api/configs", SCHOOL, Map.of("globalRules", Map.of("recommendWeights", negative)), 400);
        assertThat(r.path("message").asString()).contains("非负数");
        saveWeights(Map.of("skill", "abc"), 400);
        saveWeights(Map.of("skill", ""), 400);
        saveWeights(Map.of("unknown", 10), 400);
        saveWeights(only("none"), 400); // 全为 0
        apiPut("/api/configs", SCHOOL, Map.of("globalRules", Map.of("recommendWeights", 5)), 400);
        // 被拒后配置保持上一次保存的值
        assertThat(recommend(ALEX).path("weights").path("skill").asInt()).isEqualTo(7);

        // 只传部分项时其余补默认值
        saveWeights(Map.of("skill", 50), 200);
        JsonNode w = recommend(ALEX).path("weights");
        assertThat(w.path("skill").asInt()).isEqualTo(50);
        assertThat(w.path("hours").asInt()).isEqualTo(25);
        // 非校级管理员不能改配置
        apiPut("/api/configs", "zhang.mingyuan", Map.of("globalRules", Map.of("recommendWeights", only("dept"))), 403);
    }

    @Test
    @Order(5)
    void onlyStudentsCanCall() throws Exception {
        send(HttpMethod.GET, URL, "zhang.mingyuan", null, 403);
        send(HttpMethod.GET, URL, "li.wanqing", null, 403);
        send(HttpMethod.GET, URL, "wang.haitao", null, 403);
        send(HttpMethod.GET, URL, SCHOOL, null, 403);
        send(HttpMethod.GET, URL, null, null, 401);
        send(HttpMethod.GET, URL, "elena.rossi", null, 200);
    }
}
