package edu.cga.domain.entity;

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

/** 岗位申请（前端集合 applications） */
@Data
@TableName(value = "applications", autoResultMap = true)
public class JobApplication {
    @TableId(type = IdType.INPUT)
    private String id;
    private String jobId;
    private String studentId;
    private String status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime appliedAt;

    private String statement;
    private String resumeFileName;

    /** 申请附件 ID 列表（P1 第三批：简历与其他材料，见 attachments 表） */
    @TableField(typeHandler = JsonColumnTypeHandler.class)
    private List<String> attachmentIds;

    @TableField(typeHandler = JsonColumnTypeHandler.class)
    private Map<String, Object> complianceCheckResult;

    private Boolean isSpecialApproval;
    private String specialReason;
    private String specialStatus;

    @TableField(typeHandler = JsonColumnTypeHandler.class)
    private Map<String, Object> interviewInfo;

    private Integer score;
    private String mentorComment;
}
