package edu.cga.domain.entity;

import java.time.LocalDateTime;
import java.util.Map;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import edu.cga.domain.JsonColumnTypeHandler;
import lombok.Data;

/** 岗位模板：deptId 为空表示全校通用；payload 为除岗位名称外的发布表单字段 */
@Data
@TableName(value = "job_templates", autoResultMap = true)
public class JobTemplate {
    @TableId(type = IdType.INPUT)
    private String id;
    private String name;
    private String type;

    private String deptId;

    @TableField(typeHandler = JsonColumnTypeHandler.class)
    private Map<String, Object> payload;

    private String createdBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
