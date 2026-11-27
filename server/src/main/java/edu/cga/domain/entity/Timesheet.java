package edu.cga.domain.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

/** 周工时 */
@Data
@TableName("timesheets")
public class Timesheet {
    @TableId(type = IdType.INPUT)
    private String id;
    private String contractId;
    private String studentId;
    private String jobId;
    private String deptId;
    private Integer weekNo;
    private String month;
    private BigDecimal hours;
    private String workLog;
    private String milestoneRef;
    private String status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime submittedAt;

    private String mentorComment;
}
