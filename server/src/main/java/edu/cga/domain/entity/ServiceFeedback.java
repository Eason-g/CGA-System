package edu.cga.domain.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

/** 服务对象的一条评价；clientHash 只用于去重，不对外输出 */
@Data
@TableName("service_feedback")
public class ServiceFeedback {
    @TableId(type = IdType.INPUT)
    private String id;
    private String roundId;
    private Integer rating;
    private String comment;

    @JsonIgnore
    private String clientHash;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
