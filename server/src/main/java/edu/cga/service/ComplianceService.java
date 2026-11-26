package edu.cga.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import edu.cga.common.BizException;
import edu.cga.common.Num;
import edu.cga.common.TimeService;
import edu.cga.domain.entity.Attachment;
import edu.cga.domain.entity.User;
import edu.cga.domain.mapper.AttachmentMapper;
import edu.cga.domain.mapper.UserMapper;
import edu.cga.rule.RuleEngine;
import edu.cga.scope.AttachmentScope;
import edu.cga.scope.Masker;
import edu.cga.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 合规核验（P1 第三批 3.2）：国际生办查看学生上传的语言证明、签证材料并核验。
 * <ul>
 *   <li>语言证明核验通过 → languageValid=true（可修正 languageLevel、languageCert）；不通过 → languageValid=false；</li>
 *   <li>签证材料核验通过 → visaValid=true（可修正有效期、签证类型）；不通过只记录结果，不改动原签证信息；</li>
 *   <li>国际生办只能核验国际生的材料，校级管理员不限；每次核验写审计日志并通知学生。</li>
 * </ul>
 */
@Service
public class ComplianceService {

    private final AttachmentMapper attachmentMapper;
    private final UserMapper userMapper;
    private final AttachmentScope attachmentScope;
    private final AuditService audit;
    private final TimeService time;

    public ComplianceService(AttachmentMapper attachmentMapper, UserMapper userMapper,
                             AttachmentScope attachmentScope, AuditService audit, TimeService time) {
        this.attachmentMapper = attachmentMapper;
        this.userMapper = userMapper;
        this.attachmentScope = attachmentScope;
        this.audit = audit;
        this.time = time;
    }

    /** 核验请求；attachmentId 为空时取该生最近一份待核验的语言证明 */
    public record VerifyRequest(String attachmentId, String decision, BigDecimal languageLevel, String languageCert,
                                LocalDate visaExpiry, String visaType, String opinion) {
    }

    /** 核验结果：更新后的学生学籍合规字段 + 附件 */
    public record VerifyResult(User student, Attachment attachment) {
    }

    // ------------------------------------------------------------------ 列表

    /**
     * “合规核验”列表：可见范围内的语言证明与签证材料，附带学生当前的合规字段。待核验的排在前面。
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(String status, CurrentUser operator) {
        QueryWrapper<Attachment> w = attachmentScope.attachments(operator).applyTo(new QueryWrapper<Attachment>())
                .in("kind", Attachment.LANG_CERT, Attachment.VISA);
        if (status != null && !status.isBlank()) {
            w.eq("verify_status", status.trim());
        }
        w.orderByDesc("uploaded_at").orderByAsc("id");
        List<Attachment> rows = attachmentMapper.selectList(w);

        Map<String, User> users = new HashMap<>();
        List<Map<String, Object>> pending = new ArrayList<>();
        List<Map<String, Object>> done = new ArrayList<>();
        for (Attachment a : rows) {
            User u = users.computeIfAbsent(a.getOwnerId(), userMapper::selectById);
            if (u == null) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("attachment", a);
            item.put("student", complianceView(u));
            (RuleEngine.LANG_CERT_PENDING.equals(a.getVerifyStatus()) ? pending : done).add(item);
        }
        pending.addAll(done);
        return pending;
    }

    // ------------------------------------------------------------------ 核验

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public VerifyResult verify(String userId, VerifyRequest req, CurrentUser operator) {
        User student = userMapper.lockById(userId);
        // 国际生办只能核验国际生的材料；不在范围内按不存在处理
        if (student == null || !CurrentUser.STUDENT.equals(student.getRole())
                || (operator.is(CurrentUser.INTL_OFFICER) && !Boolean.TRUE.equals(student.getIsInternational()))) {
            throw BizException.notFound("学生不存在");
        }
        boolean approve = "APPROVE".equals(req.decision());

        Attachment att = resolveAttachment(student, req.attachmentId());
        if (!RuleEngine.LANG_CERT_PENDING.equals(att.getVerifyStatus())) {
            throw BizException.badRequest("该材料已核验（" + statusLabel(att.getVerifyStatus()) + "），无需重复处理");
        }

        String before;
        String after;
        String action;
        String opinion = JobService.blankTo(req.opinion(), approve ? "材料真实有效" : "材料不符合要求");
        if (Attachment.LANG_CERT.equals(att.getKind())) {
            before = langSummary(student);
            if (approve) {
                if (req.languageLevel() != null) {
                    BigDecimal level = req.languageLevel();
                    if (level.signum() <= 0 || level.compareTo(BigDecimal.valueOf(9)) > 0) {
                        throw BizException.badRequest("语言等效分应在 0–9 之间（雅思等效）");
                    }
                    student.setLanguageLevel(level.setScale(1, java.math.RoundingMode.HALF_UP));
                }
                if (req.languageCert() != null && !req.languageCert().isBlank()) {
                    if (req.languageCert().trim().length() > 64) {
                        throw BizException.badRequest("证书名称不能超过 64 字");
                    }
                    student.setLanguageCert(req.languageCert().trim());
                }
                student.setLanguageValid(true);
                student.setLanguageCertStatus(RuleEngine.LANG_CERT_VERIFIED);
            } else {
                student.setLanguageValid(false);
                student.setLanguageCertStatus(RuleEngine.LANG_CERT_REJECTED);
            }
            after = langSummary(student);
            action = approve ? "国际生办核验语言证明（通过）" : "国际生办核验语言证明（不通过）";
        } else {
            before = visaSummary(student);
            if (approve) {
                if (req.visaExpiry() != null) {
                    student.setVisaExpiry(req.visaExpiry());
                }
                if (req.visaType() != null && !req.visaType().isBlank()) {
                    if (req.visaType().trim().length() > 64) {
                        throw BizException.badRequest("签证类型不能超过 64 字");
                    }
                    student.setVisaType(req.visaType().trim());
                }
                if (student.getVisaExpiry() != null && student.getVisaExpiry().isBefore(time.today())) {
                    throw BizException.badRequest("签证有效期已过期，不能核验通过");
                }
                student.setVisaValid(true);
            }
            after = visaSummary(student);
            action = approve ? "国际生办核验签证材料（通过）" : "国际生办核验签证材料（不通过）";
        }
        userMapper.updateById(student);

        att.setVerifyStatus(approve ? RuleEngine.LANG_CERT_VERIFIED : RuleEngine.LANG_CERT_REJECTED);
        att.setVerifyOpinion(opinion.length() > 255 ? opinion.substring(0, 255) : opinion);
        att.setVerifiedBy(operator.id());
        att.setVerifiedAt(time.now());
        attachmentMapper.updateById(att);

        audit.notify(student.getId(), AuditService.CHANNEL_MAIL,
                AttachmentService.kindLabel(att.getKind()) + (approve ? "已核验通过" : "核验未通过"),
                "您提交的「" + att.getOriginalName() + "」" + (approve ? "已由国际合作交流处核验通过。" : "未通过核验：")
                        + (approve ? "" : opinion));
        audit.log("USER", student.getId(), operator, action, "材料 " + att.getId() + "「" + att.getOriginalName() + "」："
                + opinion, before, after);
        // 联系方式只对本人和校级管理员明文（已落库，这里只改返回对象）
        if (!operator.is(CurrentUser.SCHOOL_ADMIN)) {
            student.setPhone(Masker.phone(student.getPhone()));
            student.setEmail(Masker.email(student.getEmail()));
        }
        return new VerifyResult(student, att);
    }

    private Attachment resolveAttachment(User student, String attachmentId) {
        if (attachmentId != null && !attachmentId.isBlank()) {
            Attachment a = attachmentMapper.lockById(attachmentId.trim());
            if (a == null || !student.getId().equals(a.getOwnerId())) {
                throw BizException.notFound("附件不存在");
            }
            if (!AttachmentService.needsVerify(a.getKind())) {
                throw BizException.badRequest("只有语言证明和签证材料需要核验");
            }
            return a;
        }
        Attachment latest = attachmentMapper.selectOne(new QueryWrapper<Attachment>()
                .eq("owner_id", student.getId()).eq("kind", Attachment.LANG_CERT)
                .eq("verify_status", RuleEngine.LANG_CERT_PENDING)
                .orderByDesc("uploaded_at").orderByDesc("id").last("LIMIT 1"));
        if (latest == null) {
            throw BizException.badRequest("该生没有待核验的语言证明");
        }
        return attachmentMapper.lockById(latest.getId());
    }

    /** 列表里展示的学生合规字段（不含联系方式） */
    private static Map<String, Object> complianceView(User u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId());
        m.put("name", u.getName());
        m.put("studentNo", u.getStudentNo());
        m.put("deptId", u.getDeptId());
        m.put("isInternational", Boolean.TRUE.equals(u.getIsInternational()));
        m.put("languageCert", u.getLanguageCert());
        m.put("languageLevel", u.getLanguageLevel());
        m.put("languageValid", Boolean.TRUE.equals(u.getLanguageValid()));
        m.put("languageCertStatus", u.getLanguageCertStatus());
        m.put("visaType", u.getVisaType());
        m.put("visaValid", Boolean.TRUE.equals(u.getVisaValid()));
        m.put("visaExpiry", u.getVisaExpiry() != null ? u.getVisaExpiry().toString() : null);
        return m;
    }

    private static String langSummary(User u) {
        return Num.plain(Num.orZero(u.getLanguageLevel())) + " / " + JobService.blankTo(u.getLanguageCert(), "未提交")
                + " / " + AttachmentService.certStatusLabel(u.getLanguageCertStatus(), u.getLanguageValid());
    }

    private static String visaSummary(User u) {
        return JobService.blankTo(u.getVisaType(), "未登记") + " / 有效期至 "
                + (u.getVisaExpiry() != null ? u.getVisaExpiry() : "未登记")
                + (Boolean.TRUE.equals(u.getVisaValid()) ? " / 有效" : " / 无效");
    }

    private static String statusLabel(String s) {
        if (RuleEngine.LANG_CERT_VERIFIED.equals(s)) {
            return "已通过";
        }
        if (RuleEngine.LANG_CERT_REJECTED.equals(s)) {
            return "未通过";
        }
        return "待核验";
    }
}
