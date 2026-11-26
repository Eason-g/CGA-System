package edu.cga.scope;

import static edu.cga.security.CurrentUser.DEPT_ADMIN;
import static edu.cga.security.CurrentUser.INTL_OFFICER;
import static edu.cga.security.CurrentUser.MENTOR;
import static edu.cga.security.CurrentUser.SCHOOL_ADMIN;
import static edu.cga.security.CurrentUser.STUDENT;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import edu.cga.common.BizException;
import edu.cga.security.CurrentUser;
import org.springframework.stereotype.Component;

/**
 * 数据范围：根据当前用户的角色/院系，为每张表生成 SQL 层面的 WHERE 条件（设计文档第 6 节）。
 * <p>
 * 隔离做在 SQL 里，而不是查全表后在内存里过滤；SnapshotService 和所有按 ID 取数据的写接口都用它，
 * 不在范围内的 ID 一律当作不存在（404，见 {@link #requireVisible}）。
 *
 * <pre>
 * 角色           可见范围
 * STUDENT       已发布岗位 + 自己申请/签约过的岗位；自己的申请、合同、工时、薪酬、考核
 * MENTOR        本院系岗位 + 自己发布的岗位；自己岗位下的申请、合同、工时、薪酬、考核；本院系经费 + GA_FUND 经费
 * DEPT_ADMIN    dept_id = 本院系的全部数据；向本院系岗位投递的申请
 * INTL_OFFICER  面向国际生的岗位；国际生的申请/合同/工时及全部特批单
 * SCHOOL_ADMIN  全部
 * </pre>
 */
@Component
public class DataScope {

    /** 国际生用户 ID 子查询 */
    private static final String INTL_STUDENT_IDS = "SELECT id FROM users WHERE is_international = 1";

    // ------------------------------------------------------------------ 岗位

    public SqlCondition jobs(CurrentUser u) {
        return switch (u.role()) {
            case SCHOOL_ADMIN -> SqlCondition.ALL;
            // 已发布岗位 + 自己申请过或签约过的岗位（岗位关闭后仍能看到自己的记录）
            case STUDENT -> SqlCondition.of(
                    "status = 'PUBLISHED'"
                            + " OR id IN (SELECT job_id FROM applications WHERE student_id = ?)"
                            + " OR id IN (SELECT job_id FROM contracts WHERE student_id = ?)",
                    u.id(), u.id());
            case MENTOR -> SqlCondition.of("dept_id = ? OR mentor_id = ?", u.deptId(), u.id());
            case DEPT_ADMIN -> SqlCondition.of("dept_id = ?", u.deptId());
            // 面向国际生开放的岗位 + 国际生办可见的申请/合同所关联的岗位
            case INTL_OFFICER -> SqlCondition.anyOf(
                    SqlCondition.of("allow_international = 1"),
                    applications(u).asInSubquery("id", "job_id", "applications"),
                    contracts(u).asInSubquery("id", "job_id", "contracts"));
            default -> SqlCondition.NONE;
        };
    }

    // ------------------------------------------------------------------ 申请

    public SqlCondition applications(CurrentUser u) {
        return switch (u.role()) {
            case SCHOOL_ADMIN -> SqlCondition.ALL;
            case STUDENT -> SqlCondition.of("student_id = ?", u.id());
            case MENTOR -> SqlCondition.of("job_id IN (SELECT id FROM jobs WHERE mentor_id = ?)", u.id());
            // applications 表没有 dept_id，按岗位所属院系判断
            case DEPT_ADMIN -> SqlCondition.of("job_id IN (SELECT id FROM jobs WHERE dept_id = ?)", u.deptId());
            case INTL_OFFICER -> SqlCondition.of(
                    "is_special_approval = 1 OR student_id IN (" + INTL_STUDENT_IDS + ")");
            default -> SqlCondition.NONE;
        };
    }

    // ------------------------------------------------------------------ 合同 / 工时（国际生办可见国际生的）

    public SqlCondition contracts(CurrentUser u) {
        return studentJobDeptScope(u, true);
    }

    public SqlCondition timesheets(CurrentUser u) {
        return studentJobDeptScope(u, true);
    }

    // ------------------------------------------------------------------ 薪酬 / 考核（国际生办不可见）

    public SqlCondition payrolls(CurrentUser u) {
        return studentJobDeptScope(u, false);
    }

    public SqlCondition evaluations(CurrentUser u) {
        return studentJobDeptScope(u, false);
    }

    /**
     * contracts、timesheets、payrolls、evaluations 四张表结构相同（都有 student_id、job_id、dept_id），共用一套规则。
     */
    private SqlCondition studentJobDeptScope(CurrentUser u, boolean intlVisible) {
        return switch (u.role()) {
            case SCHOOL_ADMIN -> SqlCondition.ALL;
            case STUDENT -> SqlCondition.of("student_id = ?", u.id());
            case MENTOR -> SqlCondition.of("job_id IN (SELECT id FROM jobs WHERE mentor_id = ?)", u.id());
            case DEPT_ADMIN -> SqlCondition.of("dept_id = ?", u.deptId());
            case INTL_OFFICER -> intlVisible
                    ? SqlCondition.of("student_id IN (" + INTL_STUDENT_IDS + ")")
                    : SqlCondition.NONE;
            default -> SqlCondition.NONE;
        };
    }

    // ------------------------------------------------------------------ 经费

    public SqlCondition budgets(CurrentUser u) {
        return switch (u.role()) {
            case SCHOOL_ADMIN -> SqlCondition.ALL;
            case MENTOR -> SqlCondition.of("dept_id = ? OR source_type = 'GA_FUND'", u.deptId());
            case DEPT_ADMIN -> SqlCondition.of("dept_id = ?", u.deptId());
            // 学生只看自己薪酬单引用的经费（SnapshotService 会去掉金额，只留名称/编号/来源）
            case STUDENT -> SqlCondition.of("id IN (SELECT budget_id FROM payrolls WHERE student_id = ?)", u.id());
            default -> SqlCondition.NONE;
        };
    }

    // ------------------------------------------------------------------ 用户

    /**
     * 可见用户：本人 + 可见申请/合同涉及的学生 + 可见岗位的导师，再加各角色的额外范围。
     * 学生看到的导师只保留姓名、职称、院系（在 SnapshotService 中裁剪）。
     */
    public SqlCondition users(CurrentUser u) {
        if (u.is(SCHOOL_ADMIN)) {
            return SqlCondition.ALL;
        }
        SqlCondition self = SqlCondition.of("id = ?", u.id());
        SqlCondition mentorsOfJobs = jobs(u).asInSubquery("id", "mentor_id", "jobs");
        if (u.is(STUDENT)) {
            return SqlCondition.anyOf(self, mentorsOfJobs);
        }
        SqlCondition applicants = applications(u).asInSubquery("id", "student_id", "applications");
        SqlCondition contractors = contracts(u).asInSubquery("id", "student_id", "contracts");
        SqlCondition extra = switch (u.role()) {
            case DEPT_ADMIN -> SqlCondition.of("dept_id = ?", u.deptId());
            case INTL_OFFICER -> SqlCondition.of("is_international = 1");
            default -> SqlCondition.NONE;
        };
        return SqlCondition.anyOf(self, applicants, contractors, mentorsOfJobs, extra);
    }

    // ------------------------------------------------------------------ 通知

    /** 站内信只含本人的（校级管理员也一样） */
    public SqlCondition notifications(CurrentUser u) {
        return SqlCondition.of("user_id = ?", u.id());
    }

    // ------------------------------------------------------------------ 审计日志

    /**
     * 审计日志按 target 是否在可见范围内过滤，另外本人操作的日志总是可见。
     * 学生不看岗位审批日志（属于内部流程），只看与自己的申请、合同、工时、薪酬、考核相关的日志。
     */
    public SqlCondition auditLogs(CurrentUser u) {
        if (u.is(SCHOOL_ADMIN)) {
            return SqlCondition.ALL;
        }
        SqlCondition jobLogs = u.is(STUDENT)
                ? SqlCondition.NONE
                : jobs(u).asInSubquery("target_id", "id", "jobs").and("target_type = 'JOB'");
        return SqlCondition.anyOf(
                SqlCondition.of("operator_id = ?", u.id()),
                SqlCondition.of("target_type = 'USER' AND target_id = ?", u.id()),
                jobLogs,
                applications(u).asInSubquery("target_id", "id", "applications").and("target_type = 'APPLICATION'"),
                contracts(u).asInSubquery("target_id", "id", "contracts").and("target_type = 'CONTRACT'"),
                timesheets(u).asInSubquery("target_id", "id", "timesheets").and("target_type = 'TIMESHEET'"),
                payrolls(u).asInSubquery("target_id", "id", "payrolls").and("target_type = 'PAYROLL'"),
                evaluations(u).asInSubquery("target_id", "id", "evaluations").and("target_type = 'EVALUATION'"));
    }

    // ------------------------------------------------------------------ 按 ID 取数据

    /**
     * 在数据范围内按主键取一条记录；不存在或不在范围内都返回 404（不泄露“存在但无权”）。
     * 所有按 ID 取数据的写接口都经过这里，例如：{@code requireVisible(jobMapper, dataScope.jobs(user), jobId, "岗位不存在")}。
     */
    public <T> T requireVisible(BaseMapper<T> mapper, SqlCondition scope, String id, String notFoundMessage) {
        QueryWrapper<T> w = new QueryWrapper<T>().eq("id", id);
        T row = mapper.selectOne(scope.applyTo(w));
        if (row == null) {
            throw BizException.notFound(notFoundMessage);
        }
        return row;
    }
}
