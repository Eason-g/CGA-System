package edu.cga.domain.entity;

import java.math.BigDecimal;
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

/** 周期考核 */
@Data
@TableName(value = "evaluations", autoResultMap = true)
public class Evaluation {
    @TableId(type = IdType.INPUT)
    private String id;
    private String contractId;
    private String studentId;
    private String jobId;
    private String deptId;
    private String period;
    private String jobType;

    @TableField(typeHandler = JsonColumnTypeHandler.class)
    private List<Map<String, Object>> dimensions;

    private BigDecimal totalScore;
    private String grade;
    private BigDecimal coefficient;
    private Boolean renewalEligible;

    @TableField(typeHandler = JsonColumnTypeHandler.class)
    private Map<String, Object> courseEvalSync;

    private String evaluatorName;
    private String comment;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime evaluatedAt;
}
