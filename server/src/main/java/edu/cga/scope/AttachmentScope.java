package edu.cga.scope;

import static edu.cga.security.CurrentUser.DEPT_ADMIN;
import static edu.cga.security.CurrentUser.INTL_OFFICER;
import static edu.cga.security.CurrentUser.MENTOR;
import static edu.cga.security.CurrentUser.SCHOOL_ADMIN;

import edu.cga.security.CurrentUser;
import org.springframework.stereotype.Component;

/**
 * 材料附件的数据范围（P1 第三批 3.1），作用于 attachments 表，与 {@link DataScope} 一样在 SQL 层过滤：
 * <pre>
 * 角色           可下载 / 可查看的附件
 * 本人           自己上传的
 * MENTOR        上传人向自己岗位投递过申请
 * DEPT_ADMIN    上传人是本院系学生，或向本院系岗位投递过申请
 * INTL_OFFICER  上传人是国际生
 * SCHOOL_ADMIN  全部
 * </pre>
 * 不在范围内一律 404。
 */
@Component
public class AttachmentScope {

    public SqlCondition attachments(CurrentUser u) {
        if (u.is(SCHOOL_ADMIN)) {
            return SqlCondition.ALL;
        }
        SqlCondition own = SqlCondition.of("owner_id = ?", u.id());
        SqlCondition extra = switch (u.role()) {
            case MENTOR -> SqlCondition.of(
                    "owner_id IN (SELECT student_id FROM applications"
                            + " WHERE job_id IN (SELECT id FROM jobs WHERE mentor_id = ?))", u.id());
            case DEPT_ADMIN -> SqlCondition.of(
                    "owner_id IN (SELECT id FROM users WHERE dept_id = ? AND role = 'STUDENT')"
                            + " OR owner_id IN (SELECT student_id FROM applications"
                            + " WHERE job_id IN (SELECT id FROM jobs WHERE dept_id = ?))", u.deptId(), u.deptId());
            case INTL_OFFICER -> SqlCondition.of("owner_id IN (SELECT id FROM users WHERE is_international = 1)");
            default -> SqlCondition.NONE;
        };
        return SqlCondition.anyOf(own, extra);
    }
}
