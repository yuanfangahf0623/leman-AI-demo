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
 * AI data source raw record DO.
 */
@TableName("ai_data_source_raw_record")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiDataSourceRawRecordDO {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    @TableField("tenant_id")
    private Long tenantId;
    @TableField("knowledge_base_id")
    private Long knowledgeBaseId;
    @TableField("data_source_id")
    private Long dataSourceId;
    @TableField("sync_job_id")
    private Long syncJobId;
    @TableField("provider")
    private String provider;
    @TableField("module_name")
    private String moduleName;
    @TableField("object_type")
    private String objectType;
    @TableField("external_id")
    private String externalId;
    @TableField("source_uri")
    private String sourceUri;
    @TableField("payload_json")
    private String payloadJson;
    @TableField("payload_hash")
    private String payloadHash;
    @TableField("record_time")
    private LocalDateTime recordTime;
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
