package edu.cga.integration;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

/**
 * 学校评教系统对接接口（P2 C.1）。
 * <p>
 * 业务代码只依赖本接口；默认实现 {@link MockTeachingEvalGateway} 读取
 * {@code resources/integration/course-evaluations.json} 的演示数据。
 * 接入真实评教系统时新增一个实现类，并把配置 {@code app.integration.teaching-eval.provider} 改为对应取值即可。
 */
public interface TeachingEvalGateway {

    /** 助教相关的一道评教题目及其均分（1–5） */
    record Question(String title, BigDecimal avg) {
    }

    /**
     * 某门课程某学期的评教结果。
     *
     * @param responseCount    回收份数
     * @param enrolledCount    选课人数（可为 null）
     * @param taItemAvg        助教相关题目均分（1–5，保留两位小数）
     * @param satisfactionRate 满意度（百分比，0–100）
     * @param round            评教轮次说明，如“期中评教”
     */
    record CourseEvaluation(String courseCode, String courseName, String semester, int responseCount,
                            Integer enrolledCount, BigDecimal taItemAvg, BigDecimal satisfactionRate,
                            String round, List<Question> questions) {

        /** 考核 service 维度默认分 = 助教题目均分 × 20，保留一位小数 */
        public BigDecimal serviceScore() {
            return taItemAvg.multiply(BigDecimal.valueOf(20)).setScale(1, RoundingMode.HALF_UP);
        }
    }

    /** 数据来源名称（界面与考核记录里显示），如“模拟评教系统” */
    String sourceName();

    /** 数据来源代码，写入 courseEvalSync.sourceCode，如 MOCK */
    String sourceCode();

    /** 按课程号 + 学期查询评教结果；该课程该学期没有评教数据时返回空 */
    Optional<CourseEvaluation> find(String courseCode, String semester);
}
