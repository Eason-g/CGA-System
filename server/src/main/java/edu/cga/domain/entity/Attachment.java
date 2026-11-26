package edu.cga.domain.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

/** 材料附件（P1 第三批）：简历、语言证明、签证/居留许可、其他材料 */
@Data
@TableName("attachments")
public class Attachment {

    public static final String RESUME = "RESUME";
    public static final String LANG_CERT = "LANG_CERT";
    public static final String VISA = "VISA";
    public static final String OTHER = "OTHER";

    @TableId(type = IdType.INPUT)
    private String id;
    /** 上传人 */
    private String ownerId;
    private String kind;
    private String originalName;

    /** 磁盘文件名（随机 ID），不返回给前端 */
    @JsonIgnore
    private String storedName;

    private String contentType;
    private Long sizeBytes;
    private String sha256;
    /** LANG_CERT / VISA：PENDING 待核验、VERIFIED 已核验、REJECTED 未通过；其他类型为空 */
    private String verifyStatus;
    private String verifyOpinion;
    private String verifiedBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime verifiedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime uploadedAt;
}
