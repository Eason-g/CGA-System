package edu.cga.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import edu.cga.domain.JsonColumnTypeHandler;
import lombok.Data;

/** 可配置化中心：config_key 为 workflows / evaluationTemplates / globalRules */
@Data
@TableName(value = "sys_config", autoResultMap = true)
public class SysConfig {
    public static final String WORKFLOWS = "workflows";
    public static final String EVALUATION_TEMPLATES = "evaluationTemplates";
    public static final String GLOBAL_RULES = "globalRules";

    @TableId(type = IdType.INPUT)
    private String configKey;

    @TableField(typeHandler = JsonColumnTypeHandler.class)
    private Object configValue;
}
