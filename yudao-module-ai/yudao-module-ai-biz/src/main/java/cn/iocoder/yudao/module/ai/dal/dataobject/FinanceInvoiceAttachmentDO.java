package cn.iocoder.yudao.module.ai.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Finance invoice attachment DO.
 */
@TableName("finance_invoice_attachment")
@Data
public class FinanceInvoiceAttachmentDO {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    @TableField("invoice_id")
    private Long invoiceId;
    @TableField("file_name")
    private String fileName;
    @TableField("file_url")
    private String fileUrl;
    @TableField("object_key")
    private String objectKey;
    @TableField("file_type")
    private String fileType;
    @TableField("file_size")
    private Long fileSize;
    @TableField("file_hash")
    private String fileHash;
    @TableField("attachment_type")
    private String attachmentType;
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
    @TableField("tenant_id")
    private Long tenantId;

}
