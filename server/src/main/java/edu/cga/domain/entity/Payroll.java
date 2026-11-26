package edu.cga.domain.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

/** 月度薪酬 */
@Data
@TableName("payrolls")
public class Payroll {
    @TableId(type = IdType.INPUT)
    private String id;
    private String studentId;
    private String contractId;
    private String jobId;
    private String deptId;
    private String month;
    private BigDecimal totalHours;
    private BigDecimal hourlyRate;
    private BigDecimal coefficient;
    private BigDecimal amount;
    private String budgetId;
    private String sourceType;
    private String financeStatus;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime calculatedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime paidAt;

    private String voucherNo;

    /** 当前（最近一次）提交财务的流水号（P1 第三批） */
    private String financeSerialNo;

    /** 最近一次提交财务的时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime submittedAt;

    /** 发放失败原因（PAY_FAILED 时有值） */
    private String failReason;
}
