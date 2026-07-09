package cn.iocoder.yudao.module.ai.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Email sync state DO.
 */
@Data
@TableName("email_sync_state")
public class EmailSyncStateDO {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("tenant_id")
    private Long tenantId;

    @TableField("account")
    private String account;

    @TableField("last_uid")
    private Long lastUid;

    @TableField("last_sync_time")
    private LocalDateTime lastSyncTime;

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
