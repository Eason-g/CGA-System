package edu.cga.domain.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

/** 站内信 */
@Data
@TableName(value = "notifications", autoResultMap = true)
public class Notification {
    @TableId(type = IdType.INPUT)
    private String id;
    private String userId;
    private String channel;
    private String title;
    private String content;

    /** 前端字段 read；READ 是 MySQL 保留字，列名用 is_read */
    @TableField("is_read")
    private Boolean read;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
