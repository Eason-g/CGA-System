package edu.cga.integration;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 模拟评教系统（演示用）：启动时读取 classpath:integration/course-evaluations.json。
 * <ul>
 *   <li>按“课程号 + 学期”索引，课程号不区分大小写，前后空格忽略；</li>
 *   <li>助教题目均分 = 各题均分的平均值（两位小数）；题目分数不在 1–5、份数为负的条目视为无效并跳过（写警告日志）；</li>
 *   <li>文件缺失或格式错误时不影响启动，所有课程都返回“暂无评教数据”。</li>
 * </ul>
 * 配置 {@code app.integration.teaching-eval.provider} 缺省或为 mock 时启用。
 */
@Component
@ConditionalOnProperty(name = "app.integration.teaching-eval.provider", havingValue = "mock", matchIfMissing = true)
public class MockTeachingEvalGateway implements TeachingEvalGateway {

    private static final Logger log = LoggerFactory.getLogger(MockTeachingEvalGateway.class);

    static final String RESOURCE = "integration/course-evaluations.json";
    private static final String DEFAULT_SOURCE = "模拟评教系统";

    private final Map<String, CourseEvaluation> byKey = new LinkedHashMap<>();
    private String sourceName = DEFAULT_SOURCE;

    public MockTeachingEvalGateway(JsonMapper json) {
        load(json);
    }

    @Override
    public String sourceName() {
        return sourceName;
    }

    @Override
    public String sourceCode() {
        return "MOCK";
    }

    @Override
    public Optional<CourseEvaluation> find(String courseCode, String semester) {
        if (courseCode == null || courseCode.isBlank() || semester == null || semester.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(byKey.get(key(courseCode, semester)));
    }

    private static String key(String courseCode, String semester) {
        return courseCode.trim().toUpperCase() + "|" + semester.trim();
    }

    private void load(JsonMapper json) {
        ClassPathResource res = new ClassPathResource(RESOURCE);
        if (!res.exists()) {
            log.warn("未找到模拟评教数据 {}，所有课程将显示暂无评教数据", RESOURCE);
            return;
        }
        JsonNode root;
        try (InputStream in = res.getInputStream()) {
            root = json.readTree(in);
        } catch (IOException | RuntimeException e) {
            log.warn("模拟评教数据 {} 读取失败：{}", RESOURCE, e.getMessage());
            return;
        }
        String src = root.path("source").asString("");
        if (!src.isBlank()) {
            sourceName = src;
        }
        for (JsonNode n : root.path("evaluations")) {
            CourseEvaluation ev = parse(n);
            if (ev == null) {
                log.warn("跳过无效的模拟评教条目：{}", n);
                continue;
            }
            byKey.put(key(ev.courseCode(), ev.semester()), ev);
        }
        log.info("模拟评教系统已加载 {} 条课程评教结果", byKey.size());
    }

    private static CourseEvaluation parse(JsonNode n) {
        String code = n.path("courseCode").asString("").trim();
        String semester = n.path("semester").asString("").trim();
        int responses = n.path("responseCount").asInt(-1);
        if (code.isEmpty() || semester.isEmpty() || responses < 0) {
            return null;
        }
        List<Question> questions = new ArrayList<>();
        BigDecimal sum = BigDecimal.ZERO;
        for (JsonNode q : n.path("taQuestions")) {
            if (!q.path("avg").isNumber()) {
                return null;
            }
            BigDecimal avg = q.path("avg").decimalValue();
            if (avg.compareTo(BigDecimal.ONE) < 0 || avg.compareTo(BigDecimal.valueOf(5)) > 0) {
                return null;
            }
            questions.add(new Question(q.path("title").asString(""), avg));
            sum = sum.add(avg);
        }
        if (questions.isEmpty()) {
            return null;
        }
        BigDecimal taAvg = sum.divide(BigDecimal.valueOf(questions.size()), 2, RoundingMode.HALF_UP);
        BigDecimal satisfaction = n.path("satisfactionRate").isNumber() ? n.path("satisfactionRate").decimalValue() : null;
        if (satisfaction == null || satisfaction.signum() < 0 || satisfaction.compareTo(BigDecimal.valueOf(100)) > 0) {
            return null;
        }
        Integer enrolled = n.path("enrolledCount").isNumber() ? n.path("enrolledCount").asInt() : null;
        return new CourseEvaluation(code, n.path("courseName").asString(""), semester, responses, enrolled, taAvg,
                satisfaction, n.path("publishedAt").asString(""), List.copyOf(questions));
    }
}
