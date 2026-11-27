package edu.cga.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import edu.cga.common.BizException;
import edu.cga.common.IdGenerator;
import edu.cga.common.Num;
import edu.cga.domain.entity.Budget;
import org.springframework.stereotype.Service;

/**
 * 岗位类型差异化字段（sys_config.jobTypeSchemas，P1 设计 1.1）。
 * <p>
 * 配置形状（按岗位类型 TA/RA/GA 各一份）：
 * <pre>
 * { label, desc,
 *   fields: [{ key, label, type, required, placeholder, options: [{ value, label }], defaultValue }],
 *   allowedFundSources: ["TA_FUND"],
 *   defaults: { hourlyRate, weeklyHours, weeks, allowInternational },
 *   intlToggle: { label, hint }   // 为空表示该类型不显示“面向国际生开放”开关
 * }
 * </pre>
 * 建岗时：必填字段不能为空、select 值必须在选项内、经费来源必须在 allowedFundSources 内；未知字段原样保存。
 * 派生字段由这里统一生成：TA 的 languageRequirement（由 minLangScore 得出），
 * RA 的 milestones（由 milestonesText 每行一条生成，dueDate 按聘期均分）。
 */
@Service
public class JobTypeSchemaService {

    public static final Set<String> FIELD_TYPES = Set.of("text", "textarea", "number", "select", "lines", "date");

    public static final Set<String> FUND_SOURCES = Set.of("TA_FUND", "RA_PROJECT", "GA_FUND");

    /** 配置缺失时的默认经费来源 */
    private static final Map<String, List<String>> DEFAULT_FUND_SOURCES = Map.of(
            "TA", List.of("TA_FUND"), "RA", List.of("RA_PROJECT"), "GA", List.of("GA_FUND"));

    private static final Map<String, String> FUND_LABELS = Map.of(
            "TA_FUND", "助教经费", "RA_PROJECT", "科研项目经费", "GA_FUND", "助管专项经费");

    /** 由后端生成的字段，配置里不允许自定义同名字段 */
    private static final Set<String> DERIVED_KEYS = Set.of("languageRequirement", "milestones", "scheduleSlots");

    private static final Pattern FIELD_KEY = Pattern.compile("^[A-Za-z][A-Za-z0-9_]{0,31}$");

    private static final int MAX_FIELDS = 30;
    private static final int MAX_TEXT_LENGTH = 2000;
    private static final int MAX_LINES = 30;

    private final ConfigService configService;
    private final IdGenerator ids;

    public JobTypeSchemaService(ConfigService configService, IdGenerator ids) {
        this.configService = configService;
        this.ids = ids;
    }

    // ================================================================== 建岗：校验与派生

    /**
     * 按配置校验并整理岗位的 extraFields，返回要保存的新 Map（不修改入参）。
     *
     * @param deadline 申请截止日（RA 里程碑从截止日后的第一个周一起算）
     * @param weeks    聘期周数（RA 里程碑按聘期均分）
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> prepareExtraFields(String jobType, Map<String, Object> extraFields, Budget budget,
                                                  int weeks, LocalDate deadline) {
        Map<String, Object> schema = schemaOf(jobType);

        // 1. 经费来源必须在该岗位类型允许的范围内
        List<String> allowed = allowedFundSources(jobType, schema);
        if (budget != null && !allowed.contains(budget.getSourceType())) {
            throw BizException.badRequest(jobType + " 岗位只能使用" + fundLabels(allowed) + "，所选经费「"
                    + budget.getName() + "」属于" + FUND_LABELS.getOrDefault(budget.getSourceType(),
                    String.valueOf(budget.getSourceType())));
        }

        // 2. 按字段定义逐个校验；未知字段原样保留
        Map<String, Object> out = new LinkedHashMap<>(extraFields == null ? Map.of() : extraFields);
        Object fieldsRaw = schema.get("fields");
        if (fieldsRaw instanceof List<?> fields) {
            for (Object f : fields) {
                if (!(f instanceof Map<?, ?> fm)) {
                    continue;
                }
                Map<String, Object> field = (Map<String, Object>) fm;
                String key = String.valueOf(field.get("key"));
                String label = labelOf(field);
                String type = String.valueOf(field.getOrDefault("type", "text"));
                Object value = normalize(type, out.get(key), label);
                if (Boolean.TRUE.equals(field.get("required")) && isEmpty(value)) {
                    throw BizException.badRequest("请填写「" + label + "」");
                }
                if ("select".equals(type) && !isEmpty(value) && !inOptions(value, field.get("options"))) {
                    throw BizException.badRequest("「" + label + "」的取值不在可选项内");
                }
                if (out.containsKey(key)) {
                    out.put(key, value);
                }
            }
        }

        // 3. 派生字段
        if ("TA".equals(jobType)) {
            out.put("languageRequirement", languageRequirement(out.get("minLangScore")));
        }
        if ("RA".equals(jobType) && out.containsKey("milestonesText")) {
            List<String> lines = toLines(out.remove("milestonesText"));
            out.put("milestones", buildMilestones(lines, weeks, deadline));
        }
        return out;
    }

    /** TA 语言要求文案：minLangScore 为空或 0 时“无特殊语言限制”，否则“IELTS 6.5 及以上” */
    public static String languageRequirement(Object minLangScore) {
        BigDecimal score = Num.toDecimal(minLangScore);
        if (score == null || score.signum() <= 0) {
            return "无特殊语言限制";
        }
        return "IELTS " + score.setScale(1, RoundingMode.HALF_UP).toPlainString() + " 及以上";
    }

    /**
     * RA 里程碑：每行一条，dueDate 按聘期均分。
     * 聘期起点取申请截止日后的第一个周一（录用后的合同也从周一开始），第 i 条（从 1 起）的截止日 =
     * 起点 + 聘期天数 × i / 条数，最后一条正好落在聘期结束日。
     */
    public List<Map<String, Object>> buildMilestones(List<String> lines, int weeks, LocalDate deadline) {
        List<Map<String, Object>> list = new ArrayList<>();
        if (lines.isEmpty()) {
            return list;
        }
        LocalDate start = deadline.with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        long totalDays = Math.max(1, weeks) * 7L;
        int n = lines.size();
        for (int i = 0; i < n; i++) {
            Map<String, Object> ms = new LinkedHashMap<>();
            ms.put("id", ids.next("MS"));
            ms.put("title", lines.get(i));
            ms.put("dueDate", start.plusDays(Math.round(totalDays * (i + 1) / (double) n)).toString());
            ms.put("done", false);
            list.add(ms);
        }
        return list;
    }

    // ================================================================== 读取配置

    @SuppressWarnings("unchecked")
    private Map<String, Object> schemaOf(String jobType) {
        Object s = configService.jobTypeSchemas().get(jobType);
        return s instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    private static List<String> allowedFundSources(String jobType, Map<String, Object> schema) {
        if (schema.get("allowedFundSources") instanceof List<?> l && !l.isEmpty()) {
            return l.stream().map(String::valueOf).toList();
        }
        return DEFAULT_FUND_SOURCES.getOrDefault(jobType, List.of());
    }

    private static String fundLabels(List<String> sources) {
        return String.join("、", sources.stream().map(s -> FUND_LABELS.getOrDefault(s, s) + "（" + s + "）").toList());
    }

    // ================================================================== 配置中心保存时的校验

    /**
     * 校验 jobTypeSchemas 配置（PUT /api/configs）。不合法时抛 400，文案指明是哪个类型、哪个字段。
     */
    public static void validateSchemas(Map<String, Object> schemas) {
        for (Map.Entry<String, Object> e : schemas.entrySet()) {
            String type = e.getKey();
            if (!ConfigService.JOB_TYPES.contains(type)) {
                throw BizException.badRequest("岗位类型必须为 TA、RA 或 GA");
            }
            if (!(e.getValue() instanceof Map<?, ?> schema)) {
                throw BizException.badRequest(type + " 岗位字段配置格式错误");
            }
            validateFields(type, schema.get("fields"));
            validateFundSources(type, schema.get("allowedFundSources"));
            validateDefaults(type, schema.get("defaults"));
        }
    }

    private static void validateFields(String type, Object fieldsRaw) {
        if (fieldsRaw == null) {
            return;
        }
        if (!(fieldsRaw instanceof List<?> fields)) {
            throw BizException.badRequest(type + " 岗位字段列表格式错误");
        }
        if (fields.size() > MAX_FIELDS) {
            throw BizException.badRequest(type + " 岗位最多配置 " + MAX_FIELDS + " 个字段");
        }
        Set<String> keys = new HashSet<>();
        for (Object f : fields) {
            if (!(f instanceof Map<?, ?> field)) {
                throw BizException.badRequest(type + " 岗位字段定义格式错误");
            }
            String key = field.get("key") == null ? "" : field.get("key").toString();
            if (!FIELD_KEY.matcher(key).matches()) {
                throw BizException.badRequest(type + " 岗位字段标识「" + key + "」不合法：须以字母开头，只含字母、数字、下划线，最长 32 位");
            }
            if (DERIVED_KEYS.contains(key)) {
                throw BizException.badRequest("「" + key + "」由系统自动生成，不能配置为表单字段");
            }
            if (!keys.add(key)) {
                throw BizException.badRequest(type + " 岗位字段标识「" + key + "」重复");
            }
            Object label = field.get("label");
            if (label == null || label.toString().isBlank() || label.toString().length() > 32) {
                throw BizException.badRequest(type + " 岗位字段「" + key + "」的名称不能为空且不超过 32 字");
            }
            String ft = field.get("type") == null ? "" : field.get("type").toString();
            if (!FIELD_TYPES.contains(ft)) {
                throw BizException.badRequest(type + " 岗位字段「" + label + "」的类型「" + ft + "」不受支持");
            }
            if (field.get("required") != null && !(field.get("required") instanceof Boolean)) {
                throw BizException.badRequest(type + " 岗位字段「" + label + "」的必填标记必须为布尔值");
            }
            if ("select".equals(ft)) {
                if (!(field.get("options") instanceof List<?> opts) || opts.isEmpty()) {
                    throw BizException.badRequest(type + " 岗位下拉字段「" + label + "」至少需要一个选项");
                }
                for (Object o : opts) {
                    if (!(o instanceof Map<?, ?> om) || om.get("value") == null
                            || om.get("label") == null || om.get("label").toString().isBlank()) {
                        throw BizException.badRequest(type + " 岗位下拉字段「" + label + "」的选项须包含取值和名称");
                    }
                }
            }
        }
    }

    private static void validateFundSources(String type, Object raw) {
        if (raw == null) {
            return;
        }
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            throw BizException.badRequest(type + " 岗位至少允许一种经费来源");
        }
        for (Object s : list) {
            if (s == null || !FUND_SOURCES.contains(s.toString())) {
                throw BizException.badRequest(type + " 岗位的经费来源「" + s + "」不受支持");
            }
        }
    }

    private static void validateDefaults(String type, Object raw) {
        if (raw == null) {
            return;
        }
        if (!(raw instanceof Map<?, ?> defaults)) {
            throw BizException.badRequest(type + " 岗位默认值格式错误");
        }
        requireRange(type, "默认时薪", defaults.get("hourlyRate"), 1, 1000);
        requireRange(type, "默认周工时", defaults.get("weeklyHours"), 1, 40);
        requireRange(type, "默认聘期周数", defaults.get("weeks"), 1, 52);
    }

    private static void requireRange(String type, String label, Object v, int min, int max) {
        if (v == null) {
            return;
        }
        BigDecimal d = Num.toDecimal(v);
        if (d == null || d.compareTo(BigDecimal.valueOf(min)) < 0 || d.compareTo(BigDecimal.valueOf(max)) > 0) {
            throw BizException.badRequest(type + " 岗位" + label + "须在 " + min + "–" + max + " 之间");
        }
    }

    // ================================================================== 字段值处理

    private static String labelOf(Map<String, Object> field) {
        Object l = field.get("label");
        return l == null || l.toString().isBlank() ? String.valueOf(field.get("key")) : l.toString();
    }

    /** 按字段类型规范化取值；格式不对抛 400 */
    private static Object normalize(String type, Object value, String label) {
        if (value == null) {
            return null;
        }
        switch (type) {
            case "number" -> {
                if (value instanceof String s && s.isBlank()) {
                    return null;
                }
                BigDecimal d = Num.toDecimal(value);
                if (d == null) {
                    throw BizException.badRequest("「" + label + "」必须为数字");
                }
                return value instanceof Number ? value : d;
            }
            case "lines" -> {
                return toLines(value);
            }
            case "date" -> {
                String s = value.toString().trim();
                if (s.isEmpty()) {
                    return "";
                }
                try {
                    return LocalDate.parse(s).toString();
                } catch (DateTimeParseException e) {
                    throw BizException.badRequest("「" + label + "」必须为 yyyy-MM-dd 格式的日期");
                }
            }
            case "select" -> {
                return value;
            }
            default -> {
                if (value instanceof Map<?, ?> || value instanceof List<?>) {
                    throw BizException.badRequest("「" + label + "」必须为文本");
                }
                String s = value.toString();
                if (s.length() > MAX_TEXT_LENGTH) {
                    throw BizException.badRequest("「" + label + "」不能超过 " + MAX_TEXT_LENGTH + " 字");
                }
                return value instanceof String ? s : value;
            }
        }
    }

    /** 多行文本 → 去掉空行的字符串数组；也接受已经是数组的值 */
    static List<String> toLines(Object value) {
        List<String> lines = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object o : list) {
                if (o != null && !o.toString().isBlank()) {
                    lines.add(o.toString().trim());
                }
            }
        } else if (value != null) {
            for (String s : value.toString().split("\\r?\\n")) {
                if (!s.isBlank()) {
                    lines.add(s.trim());
                }
            }
        }
        if (lines.size() > MAX_LINES) {
            throw BizException.badRequest("多行内容最多 " + MAX_LINES + " 行");
        }
        return lines;
    }

    private static boolean isEmpty(Object v) {
        return v == null || (v instanceof String s && s.isBlank()) || (v instanceof List<?> l && l.isEmpty());
    }

    /** select 取值是否在选项内：数字按数值比较（6.5 与 "6.5" 视为相同），其余按字符串比较 */
    private static boolean inOptions(Object value, Object options) {
        if (!(options instanceof List<?> opts)) {
            return false;
        }
        for (Object o : opts) {
            Object ov = o instanceof Map<?, ?> m ? m.get("value") : o;
            if (sameValue(ov, value)) {
                return true;
            }
        }
        return false;
    }

    private static boolean sameValue(Object a, Object b) {
        if (a == null || b == null) {
            return a == b;
        }
        if (a instanceof Number || b instanceof Number) {
            BigDecimal da = Num.toDecimal(a);
            BigDecimal db = Num.toDecimal(b);
            return da != null && db != null && da.compareTo(db) == 0;
        }
        return Objects.equals(a.toString(), b.toString());
    }
}
