package edu.cga.domain.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

/** 审计日志（前端集合 auditLogs） */
@Data
@TableName(value = "audit_log", autoResultMap = true)
public class AuditLog {
    @TableId(type = IdType.INPUT)
    private String id;
    private String targetType;
    private String targetId;

    /** 操作人用户ID，仅后端留存，不返回前端 */
    @JsonIgnore
    private String operatorId;

    private String operatorName;
    private String operatorRole;
    private String action;
    private String opinion;
    private String beforeValue;
    private String afterValue;

    /** 前端字段 timestamp，列名 log_time */
    @TableField("log_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime timestamp;
}
