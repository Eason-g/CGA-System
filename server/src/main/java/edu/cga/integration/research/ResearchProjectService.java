package edu.cga.integration.research;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import edu.cga.common.BizException;
import edu.cga.domain.entity.User;
import edu.cga.domain.mapper.UserMapper;
import edu.cga.rule.RuleEngine;
import edu.cga.security.CurrentUser;
import org.springframework.stereotype.Service;

/**
 * RA 岗位绑定科研项目的校验（P2 B.3）。建岗时由 JobService 调用，发布页“校验项目”按钮也用同一套规则：
 * <ol>
 *   <li>项目编号在科研系统中存在；</li>
 *   <li>负责人是发岗导师本人；院系管理员、校级管理员代建时只要求负责人属于岗位所在院系；</li>
 *   <li>项目结束日不早于岗位预计聘期结束日（申请截止日后的第一个周一起算，加岗位周数，与里程碑排期一致）。</li>
 * </ol>
 */
@Service
public class ResearchProjectService {

    /** 单项校验结果 */
    public record Check(String key, String label, boolean passed, String detail) {
    }

    /** 校验结果；project 为空表示项目不存在 */
    public record ProjectCheck(boolean ok, String source, ResearchGateway.ResearchProject project,
                               String leaderDeptId, LocalDate jobEndDate, List<Check> checks) {

        /** 第一条未通过项的说明 */
        public String firstError() {
            return checks.stream().filter(c -> !c.passed()).map(Check::detail).findFirst().orElse(null);
        }
    }

    private final ResearchGateway gateway;
    private final UserMapper userMapper;

    public ResearchProjectService(ResearchGateway gateway, UserMapper userMapper) {
        this.gateway = gateway;
        this.userMapper = userMapper;
    }

    /**
     * 校验。deadline 或 weeks 为空时跳过聘期校验（发布页未填完整时的预览）。
     */
    public ProjectCheck check(String code, String deptId, CurrentUser operator, LocalDate deadline, Integer weeks) {
        String c = code == null ? "" : code.trim();
        List<Check> checks = new ArrayList<>();
        ResearchGateway.ResearchProject p = c.isEmpty() ? null : gateway.findProject(c).orElse(null);
        if (p == null) {
            checks.add(new Check("exists", "项目编号存在", false,
                    c.isEmpty() ? "请填写绑定的科研项目编号"
                            : "科研系统中不存在项目编号「" + c + "」，请核对后再发布"));
            return new ProjectCheck(false, gateway.sourceName(), null, null, null, checks);
        }
        checks.add(new Check("exists", "项目编号存在", true, "科研系统中存在该项目：" + p.name()));

        User leader = p.leaderId() == null ? null : userMapper.selectById(p.leaderId());
        String leaderDeptId = leader != null ? leader.getDeptId() : null;
        String leaderName = p.leaderName() != null ? p.leaderName() : (leader != null ? leader.getName() : "未登记");
        boolean proxy = operator.is(CurrentUser.DEPT_ADMIN) || operator.is(CurrentUser.SCHOOL_ADMIN);
        if (proxy) {
            boolean sameDept = leaderDeptId != null && Objects.equals(leaderDeptId, deptId);
            checks.add(new Check("leader", "项目负责人", sameDept, sameDept
                    ? "负责人 " + leaderName + " 属于本岗位所在院系（管理员代建）"
                    : "项目「" + c + "」负责人 " + leaderName + " 不属于本岗位所在院系，代建岗位只能绑定本院系导师负责的项目"));
        } else {
            boolean self = Objects.equals(p.leaderId(), operator.id());
            checks.add(new Check("leader", "项目负责人", self, self
                    ? "负责人 " + leaderName + " 为发岗导师本人"
                    : "项目「" + c + "」负责人为 " + leaderName + "，与发岗导师不一致，只能绑定本人负责的科研项目"));
        }

        LocalDate jobEnd = null;
        if (deadline != null && weeks != null) {
            jobEnd = RuleEngine.predictContractEnd(deadline, weeks);
            boolean covered = p.endDate() != null && !p.endDate().isBefore(jobEnd);
            String end = p.endDate() != null ? p.endDate().toString() : "未登记";
            checks.add(new Check("period", "项目周期覆盖聘期", covered, covered
                    ? "项目结束日 " + end + " 不早于岗位预计聘期结束日 " + jobEnd
                    : "项目「" + c + "」结束日 " + end + " 早于岗位预计聘期结束日 " + jobEnd + "，请缩短聘期或更换项目"));
        }
        boolean ok = checks.stream().allMatch(Check::passed);
        return new ProjectCheck(ok, gateway.sourceName(), p, leaderDeptId, jobEnd, checks);
    }

    /** 建岗时的硬校验：不通过抛 400，data 为校验结果 */
    public void requireValidForJob(String code, String deptId, CurrentUser operator, LocalDate deadline, int weeks) {
        ProjectCheck check = check(code, deptId, operator, deadline, weeks);
        if (!canSeeProject(check, operator)) {
            throw BizException.badRequest("科研系统中不存在该项目或项目不在可用范围");
        }
        if (!check.ok()) {
            throw new BizException(400, check.firstError(), check);
        }
    }

    /** 发布页“校验项目”：项目不存在 404 */
    public ProjectCheck lookup(String code, String deptId, CurrentUser operator, LocalDate deadline, Integer weeks) {
        String actualDept = operator.is(CurrentUser.SCHOOL_ADMIN) && deptId != null ? deptId : operator.deptId();
        ProjectCheck check = check(code, actualDept, operator, deadline, weeks);
        if (!canSeeProject(check, operator)) {
            throw BizException.notFound("科研项目不存在");
        }
        return check;
    }

    private boolean canSeeProject(ProjectCheck check, CurrentUser operator) {
        return check.project() != null && (operator.is(CurrentUser.SCHOOL_ADMIN)
                || (operator.is(CurrentUser.MENTOR) && operator.id().equals(check.project().leaderId()))
                || (operator.is(CurrentUser.DEPT_ADMIN) && Objects.equals(operator.deptId(), check.leaderDeptId())));
    }
}
