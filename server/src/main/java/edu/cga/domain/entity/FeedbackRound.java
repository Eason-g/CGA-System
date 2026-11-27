package edu.cga.domain.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

/** 服务评价轮：status 只存 OPEN/CLOSED，“已截止”由 deadline 与今天比较得出 */
@Data
@TableName("feedback_rounds")
public class FeedbackRound {
    public static final String OPEN = "OPEN";
    public static final String CLOSED = "CLOSED";

    @TableId(type = IdType.INPUT)
    private String id;
    private String contractId;
    private String code;
    private String openedBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime openedAt;

    private LocalDate deadline;
    private String status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime closedAt;
}
