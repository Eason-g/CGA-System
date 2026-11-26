package edu.cga.domain.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

/** 电子聘用协议 */
@Data
@TableName("contracts")
public class Contract {
    @TableId(type = IdType.INPUT)
    private String id;
    private String applicationId;
    private String studentId;
    private String jobId;
    private String deptId;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer weeklyHours;
    private BigDecimal hourlyRate;
    private String status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime signedAt;

    private String signatureDataUrl;
    private String signatureText;
    private Boolean complianceAgreed;
}
