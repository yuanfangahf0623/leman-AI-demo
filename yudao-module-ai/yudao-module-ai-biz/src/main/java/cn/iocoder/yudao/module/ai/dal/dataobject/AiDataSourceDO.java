package cn.iocoder.yudao.module.ai.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * AI 数据源 DO。
 */
@TableName("ai_data_source")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiDataSourceDO {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    @TableField("tenant_id")
    private Long tenantId;
    @TableField("knowledge_base_id")
    private Long knowledgeBaseId;
    @TableField("name")
    private String name;
    @TableField("type")
    private String type;
    @TableField("sync_mode")
    private String syncMode;
    @TableField("config_json")
    private String configJson;
    @TableField("sync_enabled")
    private Boolean syncEnabled;
    @TableField("last_sync_time")
    private LocalDateTime lastSyncTime;
    @TableField("status")
    private Integer status;
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
