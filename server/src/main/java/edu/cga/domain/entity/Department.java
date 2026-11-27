package edu.cga.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 院系 */
@Data
@TableName("departments")
public class Department {
    @TableId(type = IdType.INPUT)
    private String id;
    private String name;
    private String shortName;
    /** 英文全称（双语界面用，前端 localized(dept, 'name')） */
    private String nameEn;
    /** 英文简称 */
    private String shortNameEn;
    private Integer quotaLimit;
}
