package edu.cga.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import edu.cga.common.BizException;
import edu.cga.common.TimeService;
import edu.cga.domain.entity.Contract;
import edu.cga.domain.entity.Job;
import edu.cga.domain.mapper.ContractMapper;
import edu.cga.domain.mapper.JobMapper;
import edu.cga.integration.TeachingEvalGateway;
import edu.cga.integration.TeachingEvalGateway.CourseEvaluation;
import edu.cga.scope.DataScope;
import edu.cga.security.CurrentUser;
import org.springframework.stereotype.Service;

/**
 * 评教联动（P2 C.1）：按 TA 合同所属岗位的课程号 + 学期，从评教系统取回评教结果。
 * <ul>
 *   <li>{@link #query}：考核页“同步评教结果”的预览（GET /api/integration/teaching-eval）；</li>
 *   <li>{@link #syncRecord}：提交考核时由后端重新拉取并生成 courseEvalSync，不信任前端传来的数字。</li>
 * </ul>
 * 权限与提交考核一致：岗位导师本人、本院系管理员、校级管理员；合同不在数据范围内 404。
 */
@Service
public class TeachingEvalService {

    /** 课程号、学期在 TA 岗位 extraFields 中的字段名（见 jobTypeSchemas.TA） */
    static final String COURSE_CODE = "courseCode";
    static final String SEMESTER = "semester";
    static final String COURSE_NAME = "courseName";

    public static final String NO_DATA_MESSAGE = "该课程暂无评教数据";

    private final TeachingEvalGateway gateway;
    private final ContractMapper contractMapper;
    private final JobMapper jobMapper;
    private final DataScope scope;
    private final TimeService time;

    public TeachingEvalService(TeachingEvalGateway gateway, ContractMapper contractMapper, JobMapper jobMapper,
                               DataScope scope, TimeService time) {
        this.gateway = gateway;
        this.contractMapper = contractMapper;
        this.jobMapper = jobMapper;
        this.scope = scope;
        this.time = time;
    }

    /** 查询结果：available=false 时 evaluation 为空，message 说明原因 */
    public record Lookup(Contract contract, Job job, String courseCode, String courseName, String semester,
                         CourseEvaluation evaluation, String message) {
        public boolean available() {
            return evaluation != null;
        }
    }

    /** GET /api/integration/teaching-eval?contractId= 的返回内容 */
    public Map<String, Object> query(String contractId, CurrentUser operator) {
        Contract con = scope.requireVisible(contractMapper, scope.contracts(operator), contractId, "聘用协议不存在");
        Job job = jobMapper.selectById(con.getJobId());
        if (job == null) {
            throw BizException.notFound("关联岗位不存在");
        }
        requireEvaluator(job, operator);
        return toView(lookup(con, job));
    }

    /**
     * 按合同与岗位查评教结果（调用方已做数据范围与角色校验）。
     * 非 TA 岗位抛 400；TA 岗位未绑定课程号或评教系统没有该课程数据时返回 available=false。
     */
    public Lookup lookup(Contract con, Job job) {
        if (!"TA".equals(job.getType())) {
            throw BizException.badRequest("仅助教（TA）岗位的聘用协议可同步课程评教结果");
        }
        Map<String, Object> extra = job.getExtraFields() == null ? Map.of() : job.getExtraFields();
        String code = text(extra.get(COURSE_CODE));
        String semester = text(extra.get(SEMESTER));
        String name = text(extra.get(COURSE_NAME));
        if (code.isEmpty() || semester.isEmpty()) {
            return new Lookup(con, job, code, name, semester, null, "该岗位未绑定课程编号或授课学期，无法同步评教结果");
        }
        Optional<CourseEvaluation> ev = gateway.find(code, semester);
        return new Lookup(con, job, code, name, semester, ev.orElse(null), ev.isPresent() ? "" : NO_DATA_MESSAGE);
    }

    /**
     * 写入考核单的 courseEvalSync：来源、课程号、学期、份数、均分、满意度、同步时间。
     *
     * @param appliedAsDefault 导师是否选用评教折算分作为服务维度默认值
     */
    public Map<String, Object> syncRecord(Lookup l, boolean appliedAsDefault) {
        CourseEvaluation ev = l.evaluation();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("source", gateway.sourceName());
        m.put("sourceCode", gateway.sourceCode());
        m.put("courseCode", ev.courseCode());
        m.put("courseName", ev.courseName().isEmpty() ? l.courseName() : ev.courseName());
        m.put("semester", ev.semester());
        m.put("round", ev.round());
        m.put("responseCount", ev.responseCount());
        m.put("enrolledCount", ev.enrolledCount());
        m.put("avgRating", ev.taItemAvg());
        m.put("satisfactionRate", ev.satisfactionRate());
        m.put("serviceScore", ev.serviceScore());
        m.put("appliedAsDefault", appliedAsDefault);
        m.put("syncedAt", time.nowTimestamp());
        return m;
    }

    public String sourceName() {
        return gateway.sourceName();
    }

    private Map<String, Object> toView(Lookup l) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("contractId", l.contract().getId());
        m.put("jobId", l.job().getId());
        m.put("source", gateway.sourceName());
        m.put("sourceCode", gateway.sourceCode());
        m.put("courseCode", l.courseCode());
        m.put("courseName", l.courseName());
        m.put("semester", l.semester());
        m.put("available", l.available());
        m.put("message", l.message());
        CourseEvaluation ev = l.evaluation();
        if (ev != null) {
            if (!ev.courseName().isEmpty()) {
                m.put("courseName", ev.courseName());
            }
            m.put("round", ev.round());
            m.put("responseCount", ev.responseCount());
            m.put("enrolledCount", ev.enrolledCount());
            m.put("avgRating", ev.taItemAvg());
            m.put("satisfactionRate", ev.satisfactionRate());
            m.put("serviceScore", ev.serviceScore());
            List<Map<String, Object>> qs = new ArrayList<>();
            for (TeachingEvalGateway.Question q : ev.questions()) {
                qs.add(Map.of("title", q.title(), "avg", q.avg()));
            }
            m.put("questions", qs);
        }
        m.put("fetchedAt", time.nowTimestamp());
        return m;
    }

    /** 与提交考核相同：导师只能查看自己岗位的评教（院系管理员、校级管理员由数据范围约束） */
    static void requireEvaluator(Job job, CurrentUser operator) {
        if (operator.is(CurrentUser.MENTOR) && !Objects.equals(operator.id(), job.getMentorId())) {
            throw BizException.forbidden("仅岗位导师本人可同步该岗位的评教结果");
        }
    }

    private static String text(Object v) {
        return v == null ? "" : v.toString().trim();
    }
}
