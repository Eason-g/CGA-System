package edu.cga.service;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import edu.cga.common.BizException;
import edu.cga.crypto.FieldCrypto;
import edu.cga.domain.entity.User;
import edu.cga.domain.mapper.NotificationMapper;
import edu.cga.domain.mapper.UserMapper;
import edu.cga.security.CurrentUser;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 本人信息：更新个人主页（updateUserProfile）、站内信全部已读（markNotificationsRead）。
 * 两个接口都只作用于当前登录用户本人，前端传来的 userId 一律忽略。
 */
@Service
public class ProfileService {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern PHONE = Pattern.compile("^[0-9+\\-\\s]{0,32}$");

    private final UserMapper userMapper;
    private final NotificationMapper notificationMapper;
    private final AuditService audit;

    public ProfileService(UserMapper userMapper, NotificationMapper notificationMapper, AuditService audit) {
        this.userMapper = userMapper;
        this.notificationMapper = notificationMapper;
        this.audit = audit;
    }

    /** 字段为 null 表示不修改（同 mock 的 !== undefined 判断） */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public User updateProfile(String email, String phone, List<String> skills, String bio, CurrentUser operator) {
        User target = userMapper.lockById(operator.id());
        if (target == null) {
            throw BizException.notFound("用户不存在");
        }
        if (email != null) {
            String e = email.trim();
            if (!e.isEmpty() && (e.length() > 128 || !EMAIL.matcher(e).matches())) {
                throw BizException.badRequest("邮箱格式不正确");
            }
            String emailHash = FieldCrypto.get().emailHash(e);
            if (emailHash != null && emailUnavailable(e, emailHash, target.getId())) {
                throw new BizException(409, "邮箱已被占用");
            }
            target.setEmail(e);
            target.setEmailHash(emailHash);
        }
        if (phone != null) {
            String p = phone.trim();
            if (!PHONE.matcher(p).matches()) {
                throw BizException.badRequest("手机号格式不正确");
            }
            target.setPhone(p);
        }
        if (skills != null) {
            List<String> cleaned = skills.stream().filter(s -> s != null && !s.isBlank()).map(String::trim).toList();
            if (cleaned.size() > 30 || cleaned.stream().anyMatch(s -> s.length() > 32)) {
                throw BizException.badRequest("技能标签最多 30 个，每个不超过 32 字");
            }
            target.setSkills(cleaned);
        }
        if (bio != null) {
            String b = bio.trim();
            if (b.length() > 500) {
                throw BizException.badRequest("个人简介不能超过 500 字");
            }
            target.setBio(b);
        }
        try {
            userMapper.updateById(target);
        } catch (DuplicateKeyException e) {
            throw new BizException(409, "邮箱已被占用");
        }
        if (email != null && target.getEmailHash() == null) {
            // 邮箱清空：updateById 默认跳过 null 字段，检索哈希要单独置空
            userMapper.update(null, new UpdateWrapper<User>().set("email_hash", null).eq("id", target.getId()));
        }
        audit.log("USER", target.getId(), operator, "更新个人主页信息", "已更新联系方式与技能标签", "原档案", "已更新");
        return target;
    }

    private boolean emailUnavailable(String email, String hash, String userId) {
        String normalized = email.toLowerCase(Locale.ROOT);
        if (userMapper.exists(new QueryWrapper<User>().ne("id", userId)
                .apply("(LOWER(username) = {0} OR LOWER(student_no) = {0} OR email_hash = {1})", normalized, hash))) {
            return true;
        }
        // 历史重复地址保留为联系方式，但在冲突消解前不作为登录凭据。
        return userMapper.selectList(new QueryWrapper<User>().ne("id", userId).isNull("email_hash").isNotNull("email"))
                .stream().anyMatch(u -> hash.equals(FieldCrypto.get().emailHash(u.getEmail())));
    }

    /** 仅修改本人站内信的已读标记。 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public boolean markAllRead(CurrentUser operator) {
        int changed = notificationMapper.markAllRead(operator.id());
        if (changed > 0) {
            audit.log("USER", operator.id(), operator, "将个人站内信标记为已读",
                    "更新通知数：" + changed, "未读", "已读");
        }
        return true;
    }
}
