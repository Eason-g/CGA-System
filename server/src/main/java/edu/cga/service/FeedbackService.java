package edu.cga.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import edu.cga.common.BizException;
import edu.cga.common.IdGenerator;
import edu.cga.common.Num;
import edu.cga.common.TimeService;
import edu.cga.domain.entity.Contract;
import edu.cga.domain.entity.FeedbackRound;
import edu.cga.domain.entity.Job;
import edu.cga.domain.entity.ServiceFeedback;
import edu.cga.domain.entity.User;
import edu.cga.domain.mapper.ContractMapper;
import edu.cga.domain.mapper.FeedbackRoundMapper;
import edu.cga.domain.mapper.JobMapper;
import edu.cga.domain.mapper.ServiceFeedbackMapper;
import edu.cga.domain.mapper.UserMapper;
import edu.cga.scope.DataScope;
import edu.cga.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 服务对象评价（P1 设计 1.3）。
 * <ul>
 *   <li>导师（岗位导师本人）、本院系管理员、校级管理员为一份生效合同开启评价轮，生成 8 位评价码和截止日期，可关闭；</li>
 *   <li>服务对象凭评价码在公开页面匿名打 1–5 星、可写一句话；同一浏览器每轮只能提交一次（只存随机 ID 的 SHA-256）；</li>
 *   <li>助理本人只能看到平均分和条数；周期考核时 service 维度默认取“平均星级 × 20”。</li>
 * </ul>
 */
@Service
public class FeedbackService {

    /** 评价码字符集：大写字母和数字，去掉易混的 I、L、O、0、1 */
    static final char[] CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789".toCharArray();
    static final int CODE_LENGTH = 8;
    private static final Pattern CODE_PATTERN = Pattern.compile("^[ABCDEFGHJKMNPQRSTUVWXYZ2-9]{8}$");

    /** 未指定截止日期时默认开放 14 天；最长 90 天 */
    private static final int DEFAULT_DAYS = 14;
    private static final int MAX_DAYS = 90;

    private static final int MAX_COMMENT_LENGTH = 200;
    private static final Pattern CLIENT_ID = Pattern.compile("^[A-Za-z0-9_-]{16,128}$");

    /** 常见复姓（取姓氏时用） */
    private static final Set<String> COMPOUND_SURNAMES = Set.of("欧阳", "司马", "上官", "诸葛", "东方", "皇甫", "尉迟",
            "公孙", "慕容", "长孙", "宇文", "司徒", "夏侯", "轩辕", "令狐", "端木", "独孤", "南宫", "西门", "百里");
    private static final Pattern HAN_IN_BRACKETS = Pattern.compile("[（(]\\s*([\\u4e00-\\u9fa5]{2,})\\s*[)）]");
    private static final Pattern LEADING_HAN = Pattern.compile("^([\\u4e00-\\u9fa5]{2,})");

    private final FeedbackRoundMapper roundMapper;
    private final ServiceFeedbackMapper feedbackMapper;
    private final ContractMapper contractMapper;
    private final JobMapper jobMapper;
    private final UserMapper userMapper;
    private final DataScope scope;
    private final AuditService audit;
    private final IdGenerator ids;
    private final TimeService time;
    private final SecureRandom random = new SecureRandom();

    public FeedbackService(FeedbackRoundMapper roundMapper, ServiceFeedbackMapper feedbackMapper,
                           ContractMapper contractMapper, JobMapper jobMapper, UserMapper userMapper, DataScope scope,
                           AuditService audit, IdGenerator ids, TimeService time) {
        this.roundMapper = roundMapper;
        this.feedbackMapper = feedbackMapper;
        this.contractMapper = contractMapper;
        this.jobMapper = jobMapper;
        this.userMapper = userMapper;
        this.scope = scope;
        this.audit = audit;
        this.ids = ids;
        this.time = time;
    }

    /** 某合同的评价汇总：条数与平均星级（无评价时 avgRating 为 null） */
    public record FeedbackStats(long count, BigDecimal avgRating) {
        /** 考核 service 维度默认分 = 平均星级 × 20，保留一位小数；无评价返回 null */
        public BigDecimal serviceScore() {
            return avgRating == null ? null
                    : avgRating.multiply(BigDecimal.valueOf(20)).setScale(1, RoundingMode.HALF_UP);
        }

        public Map<String, Object> toMap(String contractId) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("contractId", contractId);
            m.put("count", count);
            m.put("avgRating", avgRating);
            m.put("serviceScore", serviceScore());
            return m;
        }
    }

    public FeedbackStats stats(String contractId) {
        Map<String, Object> row = feedbackMapper.statsByContract(contractId);
        long count = row == null || row.get("cnt") == null ? 0 : ((Number) row.get("cnt")).longValue();
        BigDecimal avg = count == 0 ? null : Num.toDecimal(row.get("avgRating")).setScale(2, RoundingMode.HALF_UP);
        return new FeedbackStats(count, avg);
    }

    // ================================================================== 系统内（需登录）

    /**
     * 本人可见的评价轮（含评价码、条数、均分和单条评价），可按合同过滤。
     * 范围见 {@link DataScope#feedbackRounds}：岗位导师、本院系管理员、校级管理员。
     */
    public List<Map<String, Object>> listRounds(String contractId, CurrentUser u) {
        QueryWrapper<FeedbackRound> w = scope.feedbackRounds(u).applyTo(new QueryWrapper<FeedbackRound>());
        if (contractId != null && !contractId.isBlank()) {
            w.eq("contract_id", contractId);
        }
        List<FeedbackRound> rounds = roundMapper.selectList(w.orderByDesc("opened_at").orderByAsc("id"));
        Map<String, List<ServiceFeedback>> itemsByRound = new LinkedHashMap<>();
        if (!rounds.isEmpty()) {
            List<ServiceFeedback> items = feedbackMapper.selectList(new QueryWrapper<ServiceFeedback>()
                    .in("round_id", rounds.stream().map(FeedbackRound::getId).toList())
                    .orderByDesc("created_at").orderByAsc("id"));
            for (ServiceFeedback f : items) {
                itemsByRound.computeIfAbsent(f.getRoundId(), k -> new ArrayList<>()).add(f);
            }
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (FeedbackRound r : rounds) {
            out.add(view(r, itemsByRound.getOrDefault(r.getId(), List.of())));
        }
        return out;
    }

    /** 评价汇总：合同在本人数据范围内即可查看（助理本人也能看，但只有条数和平均分） */
    public FeedbackStats summary(String contractId, CurrentUser u) {
        scope.requireVisible(contractMapper, scope.contracts(u), contractId, "聘用协议不存在");
        return stats(contractId);
    }

    /** 开启评价轮：合同须为 ACTIVE；同一合同同时只能有一轮进行中；截止日期须在今天到 90 天之间 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Map<String, Object> open(String contractId, LocalDate deadline, CurrentUser operator) {
        scope.requireVisible(contractMapper, scope.contracts(operator), contractId, "聘用协议不存在");
        Contract con = contractMapper.lockById(contractId);
        Job job = jobMapper.selectById(con.getJobId());
        requireManager(job, operator);
        if (!"ACTIVE".equals(con.getStatus())) {
            throw BizException.badRequest("只能为生效中的聘用协议开启服务评价");
        }
        LocalDate today = time.today();
        LocalDate dl = deadline != null ? deadline : today.plusDays(DEFAULT_DAYS);
        if (dl.isBefore(today) || dl.isAfter(today.plusDays(MAX_DAYS))) {
            throw BizException.badRequest("截止日期须在今天到 " + MAX_DAYS + " 天之内");
        }
        if (roundMapper.countActiveByContract(contractId, today) > 0) {
            throw BizException.badRequest("该聘用协议已有进行中的评价轮，请先关闭后再开启新一轮");
        }

        FeedbackRound r = new FeedbackRound();
        r.setId(ids.next("FR"));
        r.setContractId(contractId);
        r.setCode(newCode());
        r.setOpenedBy(operator.id());
        r.setOpenedAt(time.now());
        r.setDeadline(dl);
        r.setStatus(FeedbackRound.OPEN);
        roundMapper.insert(r);

        audit.log("CONTRACT", contractId, operator, "开启服务对象评价",
                // 审计日志对助理本人可见，不写评价码（防止助理自己刷评价）
                "岗位「" + (job != null ? job.getTitle() : con.getJobId()) + "」开启服务评价轮 " + r.getId()
                        + "，截止 " + dl,
                "无进行中的评价轮", "评价轮进行中（截止 " + dl + "）");
        return view(r, List.of());
    }

    /** 关闭评价轮：关闭后公开接口拒绝提交 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Map<String, Object> close(String roundId, CurrentUser operator) {
        scope.requireVisible(roundMapper, scope.feedbackRounds(operator), roundId, "评价轮不存在");
        FeedbackRound r = roundMapper.lockById(roundId);
        Contract con = contractMapper.selectById(r.getContractId());
        Job job = con == null ? null : jobMapper.selectById(con.getJobId());
        requireManager(job, operator);
        if (FeedbackRound.CLOSED.equals(r.getStatus())) {
            throw BizException.badRequest("该评价轮已关闭");
        }
        r.setStatus(FeedbackRound.CLOSED);
        r.setClosedAt(time.now());
        roundMapper.updateById(r);
        FeedbackStats st = stats(r.getContractId());
        audit.log("CONTRACT", r.getContractId(), operator, "关闭服务对象评价",
                "关闭评价轮 " + r.getId() + "，该协议累计评价 " + st.count() + " 条",
                "评价轮进行中", "评价轮已关闭");
        List<ServiceFeedback> items = feedbackMapper.selectList(
                new QueryWrapper<ServiceFeedback>().eq("round_id", r.getId()).orderByDesc("created_at"));
        return view(r, items);
    }

    /** 导师只能管理自己岗位的评价轮（院系管理员、校级管理员由数据范围约束） */
    private static void requireManager(Job job, CurrentUser operator) {
        if (operator.is(CurrentUser.MENTOR) && (job == null || !operator.id().equals(job.getMentorId()))) {
            throw BizException.forbidden("仅岗位导师本人可管理服务评价");
        }
    }

    private Map<String, Object> view(FeedbackRound r, List<ServiceFeedback> items) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("contractId", r.getContractId());
        m.put("code", r.getCode());
        m.put("deadline", r.getDeadline().toString());
        m.put("status", effectiveStatus(r));
        m.put("openedBy", r.getOpenedBy());
        m.put("openedAt", r.getOpenedAt() == null ? null : r.getOpenedAt().format(TimeService.TIMESTAMP_FORMAT));
        m.put("closedAt", r.getClosedAt() == null ? null : r.getClosedAt().format(TimeService.TIMESTAMP_FORMAT));
        m.put("count", items.size());
        m.put("avgRating", items.isEmpty() ? null : BigDecimal.valueOf(
                items.stream().mapToInt(f -> Num.orZero(f.getRating())).average().orElse(0))
                .setScale(2, RoundingMode.HALF_UP));
        m.put("items", items);
        return m;
    }

    /** OPEN / CLOSED / EXPIRED（未关闭但已过截止日） */
    private String effectiveStatus(FeedbackRound r) {
        if (FeedbackRound.CLOSED.equals(r.getStatus())) {
            return FeedbackRound.CLOSED;
        }
        return r.getDeadline().isBefore(time.today()) ? "EXPIRED" : FeedbackRound.OPEN;
    }

    private String newCode() {
        for (int attempt = 0; attempt < 10; attempt++) {
            char[] buf = new char[CODE_LENGTH];
            for (int i = 0; i < CODE_LENGTH; i++) {
                buf[i] = CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)];
            }
            String code = new String(buf);
            if (roundMapper.selectByCode(code) == null) {
                return code;
            }
        }
        throw new IllegalStateException("评价码生成失败，请重试");
    }

    // ================================================================== 公开接口（无需登录）

    /** 规范化评价码：去空格转大写；格式不对按不存在处理 */
    public static String normalizeCode(String code) {
        String c = code == null ? "" : code.trim().toUpperCase();
        if (!CODE_PATTERN.matcher(c).matches()) {
            throw BizException.notFound("评价码无效，请核对后重试");
        }
        return c;
    }

    /** 公开查询：岗位名称、助理称呼（只显示姓氏加“同学”）、截止日期和是否可提交 */
    public Map<String, Object> publicInfo(String rawCode) {
        String code = normalizeCode(rawCode);
        FeedbackRound r = roundMapper.selectByCode(code);
        if (r == null) {
            throw BizException.notFound("评价码无效，请核对后重试");
        }
        Contract con = contractMapper.selectById(r.getContractId());
        Job job = con == null ? null : jobMapper.selectById(con.getJobId());
        User student = con == null ? null : userMapper.selectById(con.getStudentId());
        String closedReason = closedReason(r, con);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", r.getCode());
        m.put("jobTitle", job != null ? job.getTitle() : "");
        m.put("jobType", job != null ? job.getType() : "");
        m.put("assistantName", maskedName(student != null ? student.getName() : null));
        m.put("deadline", r.getDeadline().toString());
        m.put("open", closedReason == null);
        m.put("closedReason", closedReason);
        return m;
    }

    /**
     * 公开提交：评分 1–5 的整数、一句话不超过 200 字、clientId 为浏览器生成的随机 ID。
     * 锁评价轮行后判断截止/关闭/重复提交再写入；同一 clientId 每轮只能提交一次（409）。
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Map<String, Object> publicSubmit(String rawCode, Integer rating, String comment, String clientId) {
        String code = normalizeCode(rawCode);
        if (rating == null || rating < 1 || rating > 5) {
            throw BizException.badRequest("请选择 1–5 星评分");
        }
        String text = comment == null ? "" : comment.trim();
        if (text.length() > MAX_COMMENT_LENGTH) {
            throw BizException.badRequest("评价内容不能超过 " + MAX_COMMENT_LENGTH + " 字");
        }
        if (clientId == null || !CLIENT_ID.matcher(clientId).matches()) {
            throw BizException.badRequest("客户端标识无效，请刷新页面后重试");
        }
        FeedbackRound r = roundMapper.lockByCode(code);
        if (r == null) {
            throw BizException.notFound("评价码无效，请核对后重试");
        }
        Contract con = contractMapper.selectById(r.getContractId());
        String closedReason = closedReason(r, con);
        if (closedReason != null) {
            throw BizException.badRequest(closedReason);
        }
        String hash = sha256(clientId);
        if (feedbackMapper.countByRoundAndClient(r.getId(), hash) > 0) {
            throw new BizException(409, "您已提交过本轮评价，感谢参与");
        }
        ServiceFeedback f = new ServiceFeedback();
        f.setId(ids.next("SF"));
        f.setRoundId(r.getId());
        f.setRating(rating);
        f.setComment(text);
        f.setClientHash(hash);
        f.setCreatedAt(time.now());
        feedbackMapper.insert(f);
        audit.log("FEEDBACK", f.getId(), null, "收到匿名服务评价",
                "评价轮 " + r.getId(), "", rating + " 星");

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("rating", rating);
        m.put("createdAt", f.getCreatedAt().format(TimeService.TIMESTAMP_FORMAT));
        return m;
    }

    /** 不能提交的原因；可提交返回 null。截止日当天仍可提交 */
    private String closedReason(FeedbackRound r, Contract con) {
        if (FeedbackRound.CLOSED.equals(r.getStatus())) {
            return "本轮评价已关闭";
        }
        if (r.getDeadline().isBefore(time.today())) {
            return "本轮评价已于 " + r.getDeadline() + " 截止";
        }
        if (con == null || !"ACTIVE".equals(con.getStatus())) {
            return "该助理岗位已结束，评价已停止";
        }
        return null;
    }

    /** 助理称呼：只显示姓氏加“同学”，如“陈同学”；外文名取最后一个词 */
    public static String maskedName(String name) {
        if (name == null || name.isBlank()) {
            return "助理同学";
        }
        String han = null;
        Matcher m = HAN_IN_BRACKETS.matcher(name);
        if (m.find()) {
            han = m.group(1);
        } else {
            Matcher lead = LEADING_HAN.matcher(name.trim());
            if (lead.find()) {
                han = lead.group(1);
            }
        }
        if (han != null) {
            String two = han.length() >= 3 ? han.substring(0, 2) : null;
            return (two != null && COMPOUND_SURNAMES.contains(two) ? two : han.substring(0, 1)) + "同学";
        }
        String[] parts = name.trim().split("\\s+");
        return parts[parts.length - 1] + "同学";
    }

    static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
