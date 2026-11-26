package edu.cga.domain.entity;

import java.math.BigDecimal;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 经费池 */
@Data
@TableName("budgets")
public class Budget {
    @TableId(type = IdType.INPUT)
    private String id;
    private String code;
    private String sourceType;
    private String sourceLabel;
    private String deptId;
    private String name;
    private BigDecimal totalAmount;
    private BigDecimal usedAmount;
    private BigDecimal frozenAmount;
}
