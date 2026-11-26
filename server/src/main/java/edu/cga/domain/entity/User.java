package edu.cga.domain.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import edu.cga.domain.JsonColumnTypeHandler;
import lombok.Data;

/** 用户（学生、导师、各级管理员） */
@Data
@TableName(value = "users", autoResultMap = true)
public class User {
    @TableId(type = IdType.INPUT)
    private String id;
    private String username;

    /** BCrypt 哈希，任何情况下都不序列化给前端 */
    @JsonIgnore
    private String passwordHash;

    private String name;
    private String role;
    private String roleLabel;
    private String portal;
    private Boolean preset;
    private String presetDesc;
    private String deptId;
    private Boolean isInternational;
    private String visaType;
    private Boolean visaValid;
    private LocalDate visaExpiry;
    private String languageCert;
    private BigDecimal languageLevel;
    private Boolean languageValid;
    /** 语言证明核验状态（P1 第三批）：null=种子/历史数据，PENDING 待核验，VERIFIED 已核验，REJECTED 未通过 */
    private String languageCertStatus;
    /** 学籍状态（P2 B.2，学籍系统同步）：在读 / 休学 / 毕业；非学生为空，空按在读处理 */
    private String enrollmentStatus;
    private Integer maxWeeklyHours;
    private String studentNo;
    private String degree;
    private String major;
    private String gpa;

    @TableField(typeHandler = JsonColumnTypeHandler.class)
    private List<String> skills;

    private String email;
    private String phone;
    private String title;
    private String bio;

    /**
     * 学生跨岗总负荷 { allocated, max }：不对应数据库列，只在快照里给学生行填充。
     * allocated 按该生全校全部生效和待签合同计算，只给两个数字、不给合同明细。
     */
    @TableField(exist = false)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Map<String, Integer> hoursSummary;
}
