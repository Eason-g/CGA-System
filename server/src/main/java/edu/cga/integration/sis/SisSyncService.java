package edu.cga.integration.sis;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import edu.cga.common.BizException;
import edu.cga.common.TimeService;
import edu.cga.domain.entity.User;
import edu.cga.domain.mapper.UserMapper;
import edu.cga.rule.RuleEngine;
import edu.cga.security.CurrentUser;
import edu.cga.service.AuditService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 学籍同步（P2 B.2）：从学籍系统拉取学生记录，更新本系统学生的学籍状态与签证字段，返回差异清单并写审计日志。
 * <ul>
 *   <li>校级管理员同步全部学生；国际生办只同步国际生（与快照的数据范围一致）；</li>
 *   <li>签证字段只对国际生同步；学籍系统里为空的字段不覆盖本系统已有值；</li>
 *   <li>学籍状态只接受“在读 / 休学 / 毕业”，其他取值跳过并在结果里列出；</li>
 *   <li>同步是幂等的：再次同步、数据没有变化时差异清单为空。</li>
 * </ul>
 */
@Service
public class SisSyncService {

    /** 差异清单中的一项：谁、哪个字段、旧值 → 新值 */
    public record SisDiff(String userId, String studentNo, String name, String field, String fieldLabel,
                          String before, String after) {
    }

    /** 同步结果 */
    public record SisSyncResult(String source, String syncedAt, int checked, int changedStudents,
                                List<SisDiff> diffs, List<String> unmatched, List<String> skipped) {
    }

    private final SisGateway gateway;
    private final UserMapper userMapper;
    private final AuditService audit;
    private final TimeService time;

    public SisSyncService(SisGateway gateway, UserMapper userMapper, AuditService audit, TimeService time) {
        this.gateway = gateway;
        this.userMapper = userMapper;
        this.audit = audit;
        this.time = time;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SisSyncResult sync(CurrentUser operator) {
        if (!operator.is(CurrentUser.SCHOOL_ADMIN) && !operator.is(CurrentUser.INTL_OFFICER)) {
            throw BizException.forbidden("仅校级管理员、国际生办可同步学籍");
        }
        boolean intlOnly = operator.is(CurrentUser.INTL_OFFICER);
        List<SisGateway.SisStudent> records = gateway.fetchStudents();

        Map<String, User> byStudentNo = new LinkedHashMap<>();
        for (User u : userMapper.selectList(new QueryWrapper<User>().eq("role", CurrentUser.STUDENT)
                .isNotNull("student_no"))) {
            byStudentNo.put(u.getStudentNo().trim(), u);
        }

        List<SisDiff> diffs = new ArrayList<>();
        List<String> unmatched = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        int checked = 0;
        int changedStudents = 0;
        for (SisGateway.SisStudent r : records) {
            if (r.studentNo() == null || r.studentNo().isBlank()) {
                continue;
            }
            User found = byStudentNo.get(r.studentNo().trim());
            if (found == null) {
                unmatched.add(r.studentNo().trim() + (r.name() != null ? "（" + r.name() + "）" : ""));
                continue;
            }
            boolean intl = Boolean.TRUE.equals(found.getIsInternational());
            if (intlOnly && !intl) {
                continue;
            }
            checked++;
            // 锁学生行再比较：与申请、录用的锁顺序一致（都先锁学生行）
            User student = userMapper.lockById(found.getId());
            List<SisDiff> mine = new ArrayList<>();

            String status = r.enrollmentStatus() == null ? null : r.enrollmentStatus().trim();
            if (status != null && !status.isEmpty()) {
                if (!RuleEngine.ENROLLMENT_STATUSES.contains(status)) {
                    skipped.add(student.getName() + "：学籍状态「" + status + "」无法识别，已跳过");
                } else if (!status.equals(RuleEngine.enrollmentStatusOf(student))) {
                    mine.add(diff(student, "enrollmentStatus", "学籍状态", RuleEngine.enrollmentStatusOf(student), status));
                    student.setEnrollmentStatus(status);
                }
            }
            if (intl) {
                if (r.visaType() != null && !r.visaType().isBlank()
                        && !Objects.equals(r.visaType().trim(), student.getVisaType())) {
                    mine.add(diff(student, "visaType", "签证类型", student.getVisaType(), r.visaType().trim()));
                    student.setVisaType(r.visaType().trim());
                }
                if (r.visaExpiry() != null && !r.visaExpiry().equals(student.getVisaExpiry())) {
                    mine.add(diff(student, "visaExpiry", "签证有效期", str(student.getVisaExpiry()),
                            r.visaExpiry().toString()));
                    student.setVisaExpiry(r.visaExpiry());
                }
            }
            if (!mine.isEmpty()) {
                userMapper.updateById(student);
                changedStudents++;
                diffs.addAll(mine);
                audit.log("USER", student.getId(), operator, "学籍系统同步",
                        gateway.sourceName() + "同步更新：" + String.join("；",
                                mine.stream().map(d -> d.fieldLabel() + " " + d.before() + " → " + d.after()).toList()),
                        String.join("；", mine.stream().map(d -> d.fieldLabel() + "=" + d.before()).toList()),
                        String.join("；", mine.stream().map(d -> d.fieldLabel() + "=" + d.after()).toList()));
            }
        }

        audit.log("INTEGRATION", "SIS", operator, "学籍系统同步",
                "从" + gateway.sourceName() + "比对 " + checked + " 名学生，更新 " + changedStudents + " 名，差异 "
                        + diffs.size() + " 项" + (unmatched.isEmpty() ? "" : "；本系统未开通 " + unmatched.size() + " 名"),
                "", "");
        return new SisSyncResult(gateway.sourceName(), time.nowTimestamp(), checked, changedStudents, diffs,
                unmatched, skipped);
    }

    private static SisDiff diff(User u, String field, String label, String before, String after) {
        return new SisDiff(u.getId(), u.getStudentNo(), u.getName(), field, label, before == null ? "未登记" : before,
                after);
    }

    private static String str(LocalDate d) {
        return d == null ? null : d.toString();
    }
}
