package edu.cga.security;

import edu.cga.common.BizException;
import edu.cga.domain.entity.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 当前登录用户（来自 JWT，每次请求从数据库重新加载，角色/院系以库中为准）。
 * <p>
 * 获取方式：
 * <ol>
 *   <li>Controller 参数：{@code @AuthenticationPrincipal CurrentUser user}；</li>
 *   <li>任意位置：{@link #require()}。</li>
 * </ol>
 * 业务代码里的“操作人”一律取自这里，前端传入的 operator/student 参数全部忽略。
 */
public record CurrentUser(
        String id,
        String username,
        String name,
        String role,
        String roleLabel,
        String deptId,
        boolean international) {

    public static final String STUDENT = "STUDENT";
    public static final String MENTOR = "MENTOR";
    public static final String DEPT_ADMIN = "DEPT_ADMIN";
    public static final String INTL_OFFICER = "INTL_OFFICER";
    public static final String SCHOOL_ADMIN = "SCHOOL_ADMIN";

    public static CurrentUser from(User u) {
        return new CurrentUser(u.getId(), u.getUsername(), u.getName(), u.getRole(), u.getRoleLabel(),
                u.getDeptId(), Boolean.TRUE.equals(u.getIsInternational()));
    }

    public boolean is(String r) {
        return r.equals(role);
    }

    /** 取当前用户；未登录抛 401 */
    public static CurrentUser require() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CurrentUser user) {
            return user;
        }
        throw new BizException(401, "未登录或登录已失效");
    }
}
