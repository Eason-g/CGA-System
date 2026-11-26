package edu.cga.service;

import edu.cga.common.IdGenerator;
import edu.cga.common.TimeService;
import edu.cga.domain.entity.AuditLog;
import edu.cga.domain.entity.Notification;
import edu.cga.domain.mapper.AuditLogMapper;
import edu.cga.domain.mapper.NotificationMapper;
import edu.cga.security.CurrentUser;
import org.springframework.stereotype.Service;

/**
 * 审计日志与站内信。字段与 mock 的 addAuditLog / notifications.unshift 一致；
 * 调用方处在业务事务内，日志和业务数据一起提交或回滚。
 */
@Service
public class AuditService {

    /** 站内信渠道文案（邮件只记录渠道，不真正发送） */
    public static final String CHANNEL_MAIL = "站内信 + 模拟邮件";
    public static final String CHANNEL_INBOX = "站内信";

    private final AuditLogMapper auditLogMapper;
    private final NotificationMapper notificationMapper;
    private final IdGenerator ids;
    private final TimeService time;

    public AuditService(AuditLogMapper auditLogMapper, NotificationMapper notificationMapper, IdGenerator ids,
                        TimeService time) {
        this.auditLogMapper = auditLogMapper;
        this.notificationMapper = notificationMapper;
        this.ids = ids;
        this.time = time;
    }

    /** 写一条审计日志；operatorName/operatorRole 同 mock（姓名、角色中文名），另存 operatorId */
    public AuditLog log(String targetType, String targetId, CurrentUser operator, String action, String opinion,
                        String beforeValue, String afterValue) {
        AuditLog log = new AuditLog();
        log.setId(ids.next("LOG"));
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setOperatorId(operator != null ? operator.id() : null);
        log.setOperatorName(operator != null && operator.name() != null ? operator.name() : "系统");
        log.setOperatorRole(operator != null && operator.roleLabel() != null ? operator.roleLabel() : "系统引擎");
        log.setAction(action);
        log.setOpinion(opinion == null ? "" : opinion);
        log.setBeforeValue(beforeValue == null ? "" : beforeValue);
        log.setAfterValue(afterValue == null ? "" : afterValue);
        log.setTimestamp(time.now());
        auditLogMapper.insert(log);
        return log;
    }

    /** 发一封站内信 */
    public Notification notify(String userId, String channel, String title, String content) {
        Notification n = new Notification();
        n.setId(ids.next("NOTIF"));
        n.setUserId(userId);
        n.setChannel(channel);
        n.setTitle(title);
        n.setContent(content);
        n.setRead(false);
        n.setCreatedAt(time.now());
        notificationMapper.insert(n);
        return n;
    }
}
