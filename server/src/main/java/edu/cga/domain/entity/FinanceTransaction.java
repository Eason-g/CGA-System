package edu.cga.domain.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

/** 财务流水（P1 第三批）：每次“提交财务”生成一条，财务回调按流水号幂等处理 */
@Data
@TableName("finance_transactions")
public class FinanceTransaction {

    public static final String SUBMITTED = "SUBMITTED";
    public static final String SUCCESS = "SUCCESS";
    public static final String FAILED = "FAILED";

    @TableId(type = IdType.INPUT)
    private String serialNo;
    private String payrollId;
    private BigDecimal amount;
    private String status;
    private String voucherNo;
    private String failReason;
    private String submittedBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime submittedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime callbackAt;
}
