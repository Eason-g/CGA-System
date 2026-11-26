package edu.cga.domain.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import edu.cga.domain.JsonColumnTypeHandler;
import lombok.Data;

/** 岗位 */
@Data
@TableName(value = "jobs", autoResultMap = true)
public class Job {
    @TableId(type = IdType.INPUT)
    private String id;
    private String type;
    private String title;
    private String deptId;
    private String mentorId;
    private String budgetId;
    private Integer quota;
    private Integer hiredCount;
    private BigDecimal hourlyRate;
    private Integer weeklyHours;
    private Integer weeks;
    private Boolean allowInternational;
    private String status;
    private String workflowInstanceId;

    @TableField(typeHandler = JsonColumnTypeHandler.class)
    private List<String> workflowSteps;

    private Integer currentStepIndex;
    private LocalDate deadline;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @TableField(typeHandler = JsonColumnTypeHandler.class)
    private List<String> skills;

    private String description;

    @TableField(typeHandler = JsonColumnTypeHandler.class)
    private Map<String, Object> extraFields;

    /** 岗位预留预算（建岗时冻结，驳回时释放） */
    private BigDecimal reservedAmount;
}
