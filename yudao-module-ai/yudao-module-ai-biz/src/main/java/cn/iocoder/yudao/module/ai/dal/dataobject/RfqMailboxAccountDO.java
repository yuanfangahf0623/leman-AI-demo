package cn.iocoder.yudao.module.ai.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * RFQ mailbox account DO.
 */
@Data
@TableName("rfq_mailbox_account")
public class RfqMailboxAccountDO {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("tenant_id")
    private Long tenantId;

    @TableField("account")
    private String account;

    @TableField("email_address")
    private String emailAddress;

    @TableField("host")
    private String host;

    @TableField("port")
    private Integer port;

    @TableField("username")
    private String username;

    @TableField("password_ciphertext")
    private String passwordCiphertext;

    @TableField("password_mask")
    private String passwordMask;

    @TableField("folder")
    private String folder;

    @TableField("enabled")
    private Boolean enabled;

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
