package edu.cga.service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.cga.common.BizException;
import edu.cga.common.Num;
import edu.cga.domain.entity.SysConfig;
import edu.cga.domain.mapper.SysConfigMapper;
import edu.cga.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 配置中心：读取 workflows / evaluationTemplates / globalRules，以及 saveConfigs（PUT /api/configs）。
 */
@Service
public class ConfigService {

    public static final Set<String> JOB_TYPES = Set.of("TA", "RA", "GA");

    /** 审批流里允许出现的节点（reviewJob 只认识这些节点） */
    public static final Set<String> WORKFLOW_NODES =
            Set.of("MENTOR_SUBMIT", "DEPT_REVIEW", "INTL_REVIEW", "SCHOOL_REVIEW", "PUBLISHED");

    /** 周工时上限类配置项：必须是 1–40 的整数 */
    private static final List<String> HOUR_LIMIT_KEYS = List.of("maxWeeklyHoursDefault", "maxWeeklyHoursIntl");

    private final SysConfigMapper sysConfigMapper;
    private final AuditService audit;

    public ConfigService(SysConfigMapper sysConfigMapper, AuditService audit) {
        this.sysConfigMapper = sysConfigMapper;
        this.audit = audit;
    }

    public Map<String, Object> globalRules() {
        return read(SysConfig.GLOBAL_RULES);
    }

    public Map<String, Object> workflows() {
        return read(SysConfig.WORKFLOWS);
    }

    public Map<String, Object> evaluationTemplates() {
        return read(SysConfig.EVALUATION_TEMPLATES);
    }

    public Map<String, Object> jobTypeSchemas() {
        return read(SysConfig.JOB_TYPE_SCHEMAS);
    }

    /** configs 全部配置项，形状同 mock db.configs */
    public Map<String, Object> all() {
        Map<String, Object> configs = new LinkedHashMap<>();
        configs.put(SysConfig.WORKFLOWS, workflows());
        configs.put(SysConfig.EVALUATION_TEMPLATES, evaluationTemplates());
        configs.put(SysConfig.GLOBAL_RULES, globalRules());
        configs.put(SysConfig.JOB_TYPE_SCHEMAS, jobTypeSchemas());
        return configs;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> read(String key) {
        SysConfig row = sysConfigMapper.selectById(key);
        if (row != null && row.getConfigValue() instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        return new LinkedHashMap<>();
    }

    /**
     * 保存配置（仅校级管理员，权限注解在 Controller）。
     * 考核权重之和必须为 100；工时上限必须为 1–40 的整数；审批流节点必须是系统支持的节点。
     * globalRules 与原值合并（同 mock 的 {...old, ...new}），另两项整体替换。
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Map<String, Object> save(Map<String, List<Map<String, Object>>> workflows,
                                    Map<String, List<Map<String, Object>>> evaluationTemplates,
                                    Map<String, Object> globalRules, CurrentUser operator) {
        return save(workflows, evaluationTemplates, globalRules, null, operator);
    }

    /**
     * 同上，另可保存岗位类型差异化字段 jobTypeSchemas（P1）：按岗位类型整体替换，未提交的类型保持原样；
     * 字段定义的校验规则见 {@link JobTypeSchemaService#validateSchemas}。
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Map<String, Object> save(Map<String, List<Map<String, Object>>> workflows,
                                    Map<String, List<Map<String, Object>>> evaluationTemplates,
                                    Map<String, Object> globalRules, Map<String, Object> jobTypeSchemas,
                                    CurrentUser operator) {
        if (jobTypeSchemas != null) {
            JobTypeSchemaService.validateSchemas(jobTypeSchemas);
        }
        // 先全部校验，再写入
        if (evaluationTemplates != null) {
            for (Map.Entry<String, List<Map<String, Object>>> e : evaluationTemplates.entrySet()) {
                requireJobType(e.getKey());
                BigDecimal sum = BigDecimal.ZERO;
                for (Map<String, Object> dim : e.getValue() == null ? List.<Map<String, Object>>of() : e.getValue()) {
                    if (dim == null || dim.get("key") == null || dim.get("key").toString().isBlank()) {
                        throw BizException.badRequest(e.getKey() + " 考核评价维度缺少 key");
                    }
                    BigDecimal w = Num.toDecimal(dim.get("weight"));
                    if (w == null || w.signum() < 0) {
                        throw BizException.badRequest(e.getKey() + " 考核评价维度权重必须为非负数");
                    }
                    sum = sum.add(w);
                }
                if (sum.compareTo(BigDecimal.valueOf(100)) != 0) {
                    throw BizException.badRequest(e.getKey() + " 考核评价维度权重之和必须等于 100%（当前 "
                            + Num.plain(sum) + "%）");
                }
            }
        }
        if (workflows != null) {
            for (Map.Entry<String, List<Map<String, Object>>> e : workflows.entrySet()) {
                requireJobType(e.getKey());
                for (Map<String, Object> node : e.getValue() == null ? List.<Map<String, Object>>of() : e.getValue()) {
                    Object key = node == null ? null : node.get("key");
                    if (key == null || !WORKFLOW_NODES.contains(key.toString())) {
                        throw BizException.badRequest("审批流节点「" + key + "」不受支持");
                    }
                }
            }
        }
        if (globalRules != null) {
            for (String key : HOUR_LIMIT_KEYS) {
                if (globalRules.containsKey(key)) {
                    BigDecimal v = Num.toDecimal(globalRules.get(key));
                    if (v == null || v.stripTrailingZeros().scale() > 0
                            || v.compareTo(BigDecimal.ONE) < 0 || v.compareTo(BigDecimal.valueOf(40)) > 0) {
                        throw BizException.badRequest("周工时上限必须为 1–40 的整数");
                    }
                    globalRules.put(key, v.intValueExact());
                }
            }
        }

        if (evaluationTemplates != null) {
            write(SysConfig.EVALUATION_TEMPLATES, evaluationTemplates);
        }
        if (workflows != null) {
            write(SysConfig.WORKFLOWS, workflows);
        }
        if (jobTypeSchemas != null) {
            SysConfig row = sysConfigMapper.lockByKey(SysConfig.JOB_TYPE_SCHEMAS);
            Map<String, Object> merged = new LinkedHashMap<>();
            if (row != null && row.getConfigValue() instanceof Map<?, ?> old) {
                old.forEach((k, v) -> merged.put(k.toString(), v));
            }
            merged.putAll(jobTypeSchemas);
            write(SysConfig.JOB_TYPE_SCHEMAS, merged);
        }
        if (globalRules != null) {
            SysConfig row = sysConfigMapper.lockByKey(SysConfig.GLOBAL_RULES);
            Map<String, Object> merged = new LinkedHashMap<>();
            if (row != null && row.getConfigValue() instanceof Map<?, ?> old) {
                old.forEach((k, v) -> merged.put(k.toString(), v));
            }
            merged.putAll(globalRules);
            write(SysConfig.GLOBAL_RULES, merged);
        }

        audit.log("CONFIG", "SYSTEM_CONFIG", operator, "更新校级审批流与考核模板配置",
                "新配置已即时生效，将应用于所有新建岗位审批与周期考核单据。", "原校级规则配置", "新校级规则配置（已生效）");
        return all();
    }

    private void write(String key, Object value) {
        SysConfig row = new SysConfig();
        row.setConfigKey(key);
        row.setConfigValue(value);
        if (sysConfigMapper.updateById(row) == 0) {
            sysConfigMapper.insert(row);
        }
    }

    private static void requireJobType(String type) {
        if (!JOB_TYPES.contains(type)) {
            throw BizException.badRequest("岗位类型必须为 TA、RA 或 GA");
        }
    }
}
