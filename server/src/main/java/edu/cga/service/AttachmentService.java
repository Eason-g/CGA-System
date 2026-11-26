package edu.cga.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import edu.cga.common.BizException;
import edu.cga.common.IdGenerator;
import edu.cga.common.TimeService;
import edu.cga.config.MaterialFinanceProperties;
import edu.cga.domain.entity.Attachment;
import edu.cga.domain.entity.JobApplication;
import edu.cga.domain.entity.User;
import edu.cga.domain.mapper.AttachmentMapper;
import edu.cga.domain.mapper.JobApplicationMapper;
import edu.cga.domain.mapper.UserMapper;
import edu.cga.rule.RuleEngine;
import edu.cga.scope.AttachmentScope;
import edu.cga.scope.DataScope;
import edu.cga.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

/**
 * 材料上传与下载（P1 第三批 3.1）。
 * <ul>
 *   <li>只收 pdf/jpg/png，单个 ≤ 10MB；按文件头（魔数）判断真实类型，扩展名与真实类型不符一律拒收；</li>
 *   <li>文件存 app.upload-dir，磁盘文件名为随机 ID；库里存原名、大小、SHA-256、类型、上传人；</li>
 *   <li>语言证明上传后状态为“待核验”，并把 languageValid 置为 false，核验通过前申请校验的语言项不通过（可特批）；</li>
 *   <li>下载权限见 {@link AttachmentScope}，范围外一律 404。</li>
 * </ul>
 */
@Service
public class AttachmentService {

    private static final Logger log = LoggerFactory.getLogger(AttachmentService.class);

    /** 单个文件上限 10MB（与 spring.servlet.multipart.max-file-size 一致；MockMvc 不经过容器限制，这里再校验一次） */
    public static final long MAX_SIZE = 10L * 1024 * 1024;

    /** 一次申请最多关联的附件数 */
    public static final int MAX_APPLICATION_ATTACHMENTS = 10;

    private static final Set<String> KINDS = Set.of(Attachment.RESUME, Attachment.LANG_CERT, Attachment.VISA,
            Attachment.OTHER);

    /** 识别出的真实类型 */
    private record FileType(String contentType, String ext, Set<String> allowedExts) {
    }

    private static final FileType PDF = new FileType("application/pdf", "pdf", Set.of("pdf"));
    private static final FileType PNG = new FileType("image/png", "png", Set.of("png"));
    private static final FileType JPEG = new FileType("image/jpeg", "jpg", Set.of("jpg", "jpeg"));

    /** 下载用：附件元数据 + 磁盘路径 */
    public record StoredFile(Attachment attachment, Path path) {
    }

    private final AttachmentMapper attachmentMapper;
    private final UserMapper userMapper;
    private final JobApplicationMapper applicationMapper;
    private final AttachmentScope attachmentScope;
    private final DataScope dataScope;
    private final AuditService audit;
    private final IdGenerator ids;
    private final TimeService time;
    private final Path uploadDir;

    public AttachmentService(AttachmentMapper attachmentMapper, UserMapper userMapper,
                             JobApplicationMapper applicationMapper, AttachmentScope attachmentScope,
                             DataScope dataScope, AuditService audit, IdGenerator ids, TimeService time,
                             MaterialFinanceProperties props) {
        this.attachmentMapper = attachmentMapper;
        this.userMapper = userMapper;
        this.applicationMapper = applicationMapper;
        this.attachmentScope = attachmentScope;
        this.dataScope = dataScope;
        this.audit = audit;
        this.ids = ids;
        this.time = time;
        this.uploadDir = Path.of(props.getUploadDir()).toAbsolutePath().normalize();
    }

    public Path uploadDir() {
        return uploadDir;
    }

    // ------------------------------------------------------------------ 上传

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Attachment upload(MultipartFile file, String kind, CurrentUser operator) {
        String k = kind == null ? "" : kind.trim().toUpperCase(Locale.ROOT);
        if (!KINDS.contains(k)) {
            throw BizException.badRequest("材料类型 kind 取值必须为 RESUME / LANG_CERT / VISA / OTHER");
        }
        if (file == null || file.isEmpty()) {
            throw BizException.badRequest("请选择要上传的文件");
        }
        if (file.getSize() > MAX_SIZE) {
            throw BizException.badRequest("文件大小超过 10MB 上限");
        }
        String originalName = cleanFileName(file.getOriginalFilename());
        byte[] bytes;
        try (InputStream in = file.getInputStream()) {
            bytes = in.readAllBytes();
        } catch (IOException e) {
            throw BizException.badRequest("读取上传文件失败");
        }
        if (bytes.length > MAX_SIZE) {
            throw BizException.badRequest("文件大小超过 10MB 上限");
        }
        FileType type = detect(bytes);
        if (type == null) {
            throw BizException.badRequest("只支持 PDF、JPG、PNG 文件（系统按文件内容识别类型，不以扩展名为准）");
        }
        String ext = extensionOf(originalName);
        if (!type.allowedExts().contains(ext)) {
            throw BizException.badRequest("文件扩展名与实际内容不符（实际为 " + type.ext().toUpperCase(Locale.ROOT)
                    + "），已拒收");
        }

        User owner = Attachment.LANG_CERT.equals(k) ? userMapper.lockById(operator.id()) : userMapper.selectById(operator.id());
        if (owner == null) {
            throw BizException.notFound("用户不存在");
        }

        String storedName = UUID.randomUUID().toString().replace("-", "") + "." + type.ext();
        Path target = writeFile(storedName, bytes);

        Attachment a = new Attachment();
        a.setId(ids.next("ATT"));
        a.setOwnerId(owner.getId());
        a.setKind(k);
        a.setOriginalName(originalName);
        a.setStoredName(storedName);
        a.setContentType(type.contentType());
        a.setSizeBytes((long) bytes.length);
        a.setSha256(sha256(bytes));
        a.setVerifyStatus(needsVerify(k) ? RuleEngine.LANG_CERT_PENDING : null);
        a.setUploadedAt(time.now());
        attachmentMapper.insert(a);

        String before = "";
        String after = "";
        if (Attachment.LANG_CERT.equals(k)) {
            // 新语言证明在核验前视为未通过：以最新提交的材料为准
            before = "语言证明" + certStatusLabel(owner.getLanguageCertStatus(), owner.getLanguageValid());
            owner.setLanguageCertStatus(RuleEngine.LANG_CERT_PENDING);
            owner.setLanguageValid(false);
            userMapper.updateById(owner);
            after = "语言证明待核验";
        }
        audit.log("USER", owner.getId(), operator, "上传" + kindLabel(k),
                "上传「" + originalName + "」（" + type.ext().toUpperCase(Locale.ROOT) + "，" + bytes.length + " 字节，SHA-256 "
                        + a.getSha256().substring(0, 12) + "…）", before, after.isEmpty() ? a.getId() : after);
        log.debug("附件 {} 已保存到 {}", a.getId(), target);
        return a;
    }

    // ------------------------------------------------------------------ 下载与查询

    /** 按 ID 取附件（范围外 404），并确认磁盘文件存在 */
    @Transactional(readOnly = true)
    public StoredFile load(String id, CurrentUser user) {
        Attachment a = dataScope.requireVisible(attachmentMapper, attachmentScope.attachments(user), id, "附件不存在");
        Path path = uploadDir.resolve(a.getStoredName()).normalize();
        if (!path.startsWith(uploadDir) || !Files.isRegularFile(path)) {
            throw BizException.notFound("附件文件已丢失");
        }
        return new StoredFile(a, path);
    }

    /**
     * 列出可见附件。applicationId 不为空时只返回该申请关联的附件（申请须在本人数据范围内）；
     * ownerId、kind 为可选过滤条件。
     */
    @Transactional(readOnly = true)
    public List<Attachment> list(String ownerId, String applicationId, String kind, CurrentUser user) {
        QueryWrapper<Attachment> w = attachmentScope.attachments(user).applyTo(new QueryWrapper<>());
        if (applicationId != null && !applicationId.isBlank()) {
            JobApplication app = dataScope.requireVisible(applicationMapper, dataScope.applications(user),
                    applicationId, "申请单不存在");
            List<String> attIds = app.getAttachmentIds();
            if (attIds == null || attIds.isEmpty()) {
                return List.of();
            }
            w.in("id", attIds);
        }
        if (ownerId != null && !ownerId.isBlank()) {
            w.eq("owner_id", ownerId);
        }
        if (kind != null && !kind.isBlank()) {
            w.eq("kind", kind.trim().toUpperCase(Locale.ROOT));
        }
        w.orderByDesc("uploaded_at").orderByAsc("id");
        return attachmentMapper.selectList(w);
    }

    /**
     * 申请关联附件：去重后校验每个 ID 都存在且由该学生本人上传，返回规范化的 ID 列表。
     */
    public List<Attachment> requireOwned(List<String> attachmentIds, String studentId) {
        if (attachmentIds == null || attachmentIds.isEmpty()) {
            return List.of();
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String id : attachmentIds) {
            if (id != null && !id.isBlank()) {
                unique.add(id.trim());
            }
        }
        if (unique.size() > MAX_APPLICATION_ATTACHMENTS) {
            throw BizException.badRequest("每份申请最多关联 " + MAX_APPLICATION_ATTACHMENTS + " 个附件");
        }
        List<Attachment> result = new ArrayList<>();
        for (String id : unique) {
            Attachment a = attachmentMapper.selectById(id);
            if (a == null || !studentId.equals(a.getOwnerId())) {
                throw BizException.badRequest("附件 " + id + " 不存在或不是本人上传的材料");
            }
            result.add(a);
        }
        return result;
    }

    // ------------------------------------------------------------------ 工具

    /** 按文件头识别：PDF「%PDF-」、PNG 8 字节签名、JPEG「FF D8 FF」 */
    static FileType detect(byte[] b) {
        if (b.length >= 5 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F' && b[4] == '-') {
            return PDF;
        }
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A) {
            return PNG;
        }
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return JPEG;
        }
        return null;
    }

    private Path writeFile(String storedName, byte[] bytes) {
        Path target = uploadDir.resolve(storedName);
        try {
            Files.createDirectories(uploadDir);
            Files.write(target, bytes, StandardOpenOption.CREATE_NEW);
        } catch (IOException e) {
            throw new UncheckedIOException("保存上传文件失败", e);
        }
        // 事务回滚时删除已写入的文件，避免留下孤儿文件
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) {
                        try {
                            Files.deleteIfExists(target);
                        } catch (IOException ignored) {
                            // 清理失败不影响业务结果
                        }
                    }
                }
            });
        }
        return target;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 去掉路径部分和控制字符，限制长度；空名给默认值 */
    static String cleanFileName(String name) {
        String n = name == null ? "" : name;
        n = n.replace('\\', '/');
        n = n.substring(n.lastIndexOf('/') + 1);
        n = n.replaceAll("[\\p{Cntrl}\"]", "").trim();
        if (n.isEmpty()) {
            n = "未命名文件";
        }
        if (n.length() > 120) {
            String ext = extensionOf(n);
            n = n.substring(0, 110) + (ext.isEmpty() ? "" : "." + ext);
        }
        return n;
    }

    static String extensionOf(String name) {
        int dot = name == null ? -1 : name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    static boolean needsVerify(String kind) {
        return Attachment.LANG_CERT.equals(kind) || Attachment.VISA.equals(kind);
    }

    public static String kindLabel(String kind) {
        return switch (kind) {
            case Attachment.RESUME -> "个人简历";
            case Attachment.LANG_CERT -> "语言证明";
            case Attachment.VISA -> "签证/居留许可材料";
            default -> "其他材料";
        };
    }

    static String certStatusLabel(String status, Boolean valid) {
        if (RuleEngine.LANG_CERT_PENDING.equals(status)) {
            return "待核验";
        }
        if (RuleEngine.LANG_CERT_REJECTED.equals(status)) {
            return "核验未通过";
        }
        return Boolean.TRUE.equals(valid) ? "有效" : "无效";
    }
}
