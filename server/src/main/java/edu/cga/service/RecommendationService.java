package edu.cga.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import edu.cga.common.BizException;
import edu.cga.common.Num;
import edu.cga.common.TimeService;
import edu.cga.domain.entity.Contract;
import edu.cga.domain.entity.Job;
import edu.cga.domain.entity.JobApplication;
import edu.cga.domain.entity.User;
import edu.cga.domain.mapper.ContractMapper;
import edu.cga.domain.mapper.JobApplicationMapper;
import edu.cga.domain.mapper.JobMapper;
import edu.cga.domain.mapper.UserMapper;
import edu.cga.rule.RuleEngine;
import edu.cga.scope.DataScope;
import edu.cga.security.CurrentUser;
import org.springframework.stereotype.Service;

/**
 * 智能推荐（P2 C.2）：GET /api/recommendations，学生本人。
 * <p>
 * 对每个已发布、本人未申请的岗位：
 * <ol>
 *   <li>先跑 {@link RuleEngine#validateStudentApplication}（与提交申请同一套校验）。硬性条件（签证、岗位国际生开放资格）
 *       不过、名额已满或已过截止日的岗位不推荐，只列为“不可申请”并给出原因，分数记 0、排在最后；</li>
 *   <li>其余岗位按五项因子打分，每项取值 0–1：
 *     <ul>
 *       <li>skill 技能匹配：岗位技能与学生技能的重合比例（岗位未列技能时取 0.5）；</li>
 *       <li>hours 剩余工时：录用后累计周工时不超过上限的 80% 取 1，超过 80% 但不超上限取 0.6，超上限取 0（需特批）；</li>
 *       <li>language 语言要求：无要求或已满足取 1，不满足取 0（需特批）；</li>
 *       <li>dept 同院系：岗位与学生同院系取 1，否则 0；</li>
 *       <li>deadline 截止临近：7 天内截止取 1，此后线性递减，60 天及以上取 0（未设截止日取 0.5）；</li>
 *     </ul>
 *   </li>
 *   <li>推荐分 = Σ(权重 × 取值) / Σ权重 × 100，四舍五入为整数。权重读配置 globalRules.recommendWeights，
 *       缺省为 {@link #DEFAULT_WEIGHTS}；权重之和不必为 100，按比例归一。</li>
 * </ol>
 * 排序：可申请在前，按分数降序；同分按截止日早的在前，再按岗位 ID。
 */
@Service
public class RecommendationService {

    public static final String CONFIG_KEY = "recommendWeights";

    /** 默认权重（种子配置与此一致） */
    public static final Map<String, Integer> DEFAULT_WEIGHTS = orderedOf(
            "skill", 40, "hours", 25, "language", 15, "dept", 10, "deadline", 10);

    /** 因子中文名（配置中心与推荐理由里使用） */
    public static final Map<String, String> WEIGHT_LABELS = Map.of(
            "skill", "技能匹配", "hours", "剩余周工时", "language", "语言要求", "dept", "同院系", "deadline", "截止临近");

    /** 截止日在该天数以内视为“临近”，因子取 1 */
    static final int DEADLINE_URGENT_DAYS = 7;
    /** 截止日在该天数及以后，因子取 0 */
    static final int DEADLINE_FAR_DAYS = 60;
    /** 录用后工时占上限超过该比例时，工时因子打折 */
    static final double HOURS_CAUTION_RATIO = 0.8;

    private final JobMapper jobMapper;
    private final UserMapper userMapper;
    private final JobApplicationMapper applicationMapper;
    private final ContractMapper contractMapper;
    private final DataScope scope;
    private final ConfigService configService;
    private final TimeService time;

    public RecommendationService(JobMapper jobMapper, UserMapper userMapper, JobApplicationMapper applicationMapper,
                                 ContractMapper contractMapper, DataScope scope, ConfigService configService,
                                 TimeService time) {
        this.jobMapper = jobMapper;
        this.userMapper = userMapper;
        this.applicationMapper = applicationMapper;
        this.contractMapper = contractMapper;
        this.scope = scope;
        this.configService = configService;
        this.time = time;
    }

    /** 单项因子：value 为 0–1 的取值，points 为它对总分的贡献（满分 100 中的分数） */
    public record Factor(String key, String label, Number weight, double value, double points, String detail) {
    }

    /** 一个岗位的推荐结果 */
    public record Item(String jobId, String title, String type, String deptId, BigDecimal hourlyRate,
                       Integer weeklyHours, LocalDate deadline, int score, boolean applicable,
                       boolean needsSpecialApproval, List<String> reasons, List<String> blockers,
                       List<String> matchedSkills, List<Factor> factors) {
    }

    /** 学生本人的推荐列表 */
    public Map<String, Object> recommend(CurrentUser operator) {
        User student = userMapper.selectById(operator.id());
        if (student == null || !CurrentUser.STUDENT.equals(student.getRole())) {
            throw BizException.forbidden("仅学生本人可查看岗位推荐");
        }
        Map<String, Object> globalRules = configService.globalRules();
        Map<String, Number> weights = weightsFrom(globalRules);
        LocalDate today = time.today();

        List<Job> published = jobMapper.selectList(scope.jobs(operator).applyTo(new QueryWrapper<Job>())
                .eq("status", "PUBLISHED").orderByAsc("id"));
        List<JobApplication> myApps = applicationMapper.selectByStudent(student.getId());
        // 跨岗累计工时按该生在全校的全部合同计算（与申请校验一致）
        List<Contract> myContracts = contractMapper.selectAllocatingByStudent(student.getId());

        List<Item> items = new ArrayList<>();
        int currentHours = RuleEngine.getStudentAllocatedWeeklyHours(myContracts, student.getId(), today, null);
        int maxHours = RuleEngine.getMaxWeeklyHours(globalRules, student);
        for (Job job : published) {
            RuleEngine.ApplicationCheck check = RuleEngine.validateStudentApplication(student, job, myApps,
                    myContracts, globalRules, today);
            if (check.alreadyApplied()) {
                continue; // 只推荐未申请的岗位
            }
            items.add(score(student, job, check, weights, today));
        }
        items.sort(Comparator.comparing(Item::applicable).reversed()
                .thenComparing(Comparator.comparingInt(Item::score).reversed())
                .thenComparing(Item::deadline, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Item::jobId));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("weights", weights);
        out.put("currentHours", currentHours);
        out.put("maxHours", maxHours);
        out.put("generatedAt", time.nowTimestamp());
        out.put("topJobIds", items.stream().filter(Item::applicable).limit(3).map(Item::jobId).toList());
        out.put("items", items);
        return out;
    }

    /** 给一个岗位打分（纯计算，便于单独测试） */
    static Item score(User student, Job job, RuleEngine.ApplicationCheck check, Map<String, Number> weights,
                      LocalDate today) {
        List<String> reasons = new ArrayList<>();
        List<String> blockers = new ArrayList<>();

        // 1. 合规：硬性条件、名额、截止日
        List<RuleEngine.Check> hardFailures = check.checks().stream()
                .filter(c -> !c.passed() && !c.overridable()).toList();
        hardFailures.forEach(c -> blockers.add(c.label() + "未通过：" + c.detail()));
        if (check.quotaFull()) {
            blockers.add("该岗位名额已招满");
        }
        Long daysLeft = job.getDeadline() == null ? null : ChronoUnit.DAYS.between(today, job.getDeadline());
        boolean applicable = blockers.isEmpty();

        // 2. 技能匹配
        List<String> jobSkills = job.getSkills() == null ? List.of() : job.getSkills();
        Set<String> mySkills = (student.getSkills() == null ? List.<String>of() : student.getSkills()).stream()
                .filter(Objects::nonNull).map(s -> s.trim().toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        List<String> matched = jobSkills.stream()
                .filter(s -> s != null && mySkills.contains(s.trim().toLowerCase(Locale.ROOT))).toList();
        double skillValue = jobSkills.isEmpty() ? 0.5 : (double) matched.size() / jobSkills.size();
        String skillDetail = jobSkills.isEmpty() ? "岗位未列技能要求"
                : "技能匹配 " + matched.size() + "/" + jobSkills.size()
                + (matched.isEmpty() ? "" : "：" + String.join("、", matched));

        // 3. 剩余工时（与申请校验的 hours 项同口径）
        RuleEngine.Check hoursCheck = findCheck(check, "hours");
        double hoursValue;
        String hoursDetail;
        if (hoursCheck != null && !hoursCheck.passed()) {
            hoursValue = 0;
            hoursDetail = "录用后周工时 " + check.projectedHours() + "h，超出上限 " + check.maxHours() + "h";
            if (hoursCheck.overridable()) {
                blockers.add("跨岗周工时将超上限（" + check.projectedHours() + "h > " + check.maxHours() + "h），需国际生办特批");
            }
        } else if (check.maxHours() > 0 && check.projectedHours() > check.maxHours() * HOURS_CAUTION_RATIO) {
            hoursValue = 0.6;
            hoursDetail = "录用后周工时 " + check.projectedHours() + "/" + check.maxHours() + "h，接近上限";
        } else {
            hoursValue = 1;
            hoursDetail = "录用后周工时 " + check.projectedHours() + "/" + check.maxHours() + "h，余量充足";
        }

        // 4. 语言要求
        RuleEngine.Check langCheck = findCheck(check, "lang");
        double langValue;
        String langDetail;
        if (langCheck == null) {
            langValue = 1;
            langDetail = "岗位无语言成绩要求";
        } else if (langCheck.passed()) {
            langValue = 1;
            langDetail = "语言能力满足岗位要求";
        } else {
            langValue = 0;
            langDetail = "语言能力未达岗位要求";
            blockers.add("语言要求未满足，需特批：" + langCheck.detail());
        }

        // 5. 同院系
        boolean sameDept = Objects.equals(student.getDeptId(), job.getDeptId());
        double deptValue = sameDept ? 1 : 0;
        String deptDetail = sameDept ? "本院系岗位" : "跨院系岗位";

        // 6. 截止临近
        double deadlineValue;
        String deadlineDetail;
        if (daysLeft == null) {
            deadlineValue = 0.5;
            deadlineDetail = "未设置截止日期";
        } else if (daysLeft < 0) {
            deadlineValue = 0;
            deadlineDetail = "已截止";
        } else if (daysLeft <= DEADLINE_URGENT_DAYS) {
            deadlineValue = 1;
            deadlineDetail = daysLeft == 0 ? "今天截止" : daysLeft + " 天后截止";
        } else {
            deadlineValue = Math.max(0, (double) (DEADLINE_FAR_DAYS - daysLeft) / (DEADLINE_FAR_DAYS - DEADLINE_URGENT_DAYS));
            deadlineDetail = daysLeft + " 天后截止";
        }

        List<Factor> factors = new ArrayList<>();
        Map<String, Double> values = orderedOf("skill", skillValue, "hours", hoursValue, "language", langValue,
                "dept", deptValue, "deadline", deadlineValue);
        Map<String, String> details = Map.of("skill", skillDetail, "hours", hoursDetail, "language", langDetail,
                "dept", deptDetail, "deadline", deadlineDetail);
        double weightSum = weights.values().stream().mapToDouble(Number::doubleValue).sum();
        double total = 0;
        for (Map.Entry<String, Double> e : values.entrySet()) {
            Number w = weights.getOrDefault(e.getKey(), 0);
            double points = weightSum <= 0 ? 0 : w.doubleValue() * e.getValue() / weightSum * 100;
            total += points;
            factors.add(new Factor(e.getKey(), WEIGHT_LABELS.get(e.getKey()), w, round(e.getValue(), 2),
                    round(points, 1), details.get(e.getKey())));
        }
        int score = applicable ? (int) Math.round(total) : 0;

        // 推荐理由：按贡献从高到低取有意义的因子
        if (applicable) {
            factors.stream()
                    .filter(f -> f.points() > 0 && f.value() >= 0.5)
                    .sorted(Comparator.comparingDouble(Factor::points).reversed())
                    .forEach(f -> reasons.add(f.detail()));
            if (reasons.isEmpty()) {
                reasons.add("满足申请条件");
            }
        }
        boolean needsSpecial = applicable && !blockers.isEmpty();
        return new Item(job.getId(), job.getTitle(), job.getType(), job.getDeptId(), job.getHourlyRate(),
                job.getWeeklyHours(), job.getDeadline(), score, applicable, needsSpecial, reasons, blockers,
                matched, factors);
    }

    // ================================================================== 权重配置

    /** 读配置中的推荐权重；缺项或取值无效时用默认值 */
    public static Map<String, Number> weightsFrom(Map<String, Object> globalRules) {
        Object raw = globalRules == null ? null : globalRules.get(CONFIG_KEY);
        Map<?, ?> m = raw instanceof Map<?, ?> map ? map : Map.of();
        Map<String, Number> out = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : DEFAULT_WEIGHTS.entrySet()) {
            BigDecimal v = Num.toDecimal(m.get(e.getKey()));
            out.put(e.getKey(), v == null || v.signum() < 0 ? e.getValue() : plainNumber(v));
        }
        if (out.values().stream().allMatch(n -> n.doubleValue() == 0)) {
            return new LinkedHashMap<String, Number>(DEFAULT_WEIGHTS);
        }
        return out;
    }

    /**
     * 配置中心保存时校验 recommendWeights：必须是对象，只能包含已知因子，取值为非负数且不能全为 0。
     * 缺少的因子补默认值。返回规范化后的权重（整数存为整数）。
     */
    public static Map<String, Object> validateWeights(Object raw) {
        if (!(raw instanceof Map<?, ?> m)) {
            throw BizException.badRequest("推荐权重格式不正确");
        }
        for (Object k : m.keySet()) {
            if (!DEFAULT_WEIGHTS.containsKey(String.valueOf(k))) {
                throw BizException.badRequest("未知的推荐权重项「" + k + "」");
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        BigDecimal sum = BigDecimal.ZERO;
        for (Map.Entry<String, Integer> e : DEFAULT_WEIGHTS.entrySet()) {
            Object v = m.get(e.getKey());
            if (v == null) {
                out.put(e.getKey(), e.getValue());
                sum = sum.add(BigDecimal.valueOf(e.getValue()));
                continue;
            }
            boolean parsable = v instanceof Number || (v instanceof String s && !s.isBlank());
            BigDecimal d = parsable ? Num.toDecimal(v) : null;
            if (d == null || d.signum() < 0) {
                throw BizException.badRequest("推荐权重「" + WEIGHT_LABELS.get(e.getKey()) + "」必须为非负数");
            }
            out.put(e.getKey(), plainNumber(d));
            sum = sum.add(d);
        }
        if (sum.signum() == 0) {
            throw BizException.badRequest("推荐权重不能全部为 0");
        }
        return out;
    }

    private static Number plainNumber(BigDecimal v) {
        BigDecimal s = v.stripTrailingZeros();
        if (s.scale() <= 0) {
            try {
                return s.intValueExact();
            } catch (ArithmeticException ex) {
                return s.doubleValue();
            }
        }
        return s.setScale(Math.min(s.scale(), 4), RoundingMode.HALF_UP).doubleValue();
    }

    private static RuleEngine.Check findCheck(RuleEngine.ApplicationCheck check, String key) {
        return check.checks().stream().filter(c -> key.equals(c.key())).findFirst().orElse(null);
    }

    private static double round(double v, int scale) {
        return BigDecimal.valueOf(v).setScale(scale, RoundingMode.HALF_UP).doubleValue();
    }

    @SuppressWarnings("unchecked")
    private static <V> Map<String, V> orderedOf(Object... kv) {
        Map<String, V> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], (V) kv[i + 1]);
        }
        return java.util.Collections.unmodifiableMap(m);
    }
}
