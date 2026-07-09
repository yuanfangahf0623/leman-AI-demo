package cn.iocoder.yudao.module.ai.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Email attachment metadata and extracted text.
 */
@Data
@TableName("email_attachment")
public class EmailAttachmentDO {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("tenant_id")
    private Long tenantId;

    @TableField("account")
    private String account;

    @TableField("message_id")
    private String messageId;

    @TableField("message_hash")
    private String messageHash;

    @TableField("uid")
    private Long uid;

    @TableField("rfq_id")
    private Long rfqId;

    @TableField("file_name")
    private String fileName;

    @TableField("content_type")
    private String contentType;

    @TableField("file_size")
    private Long fileSize;

    @TableField("object_key")
    private String objectKey;

    @TableField("source_uri")
    private String sourceUri;

    @TableField("content_hash")
    private String contentHash;

    @TableField("extracted_text")
    private String extractedText;

    @TableField("creator")
    private String creator;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("updater")
    private String updater;

    @TableField("update_time")
    private LocalDateTime updateTime;

    @TableLogic
    @TableField("deleted")
    private Boolean deleted;

}
