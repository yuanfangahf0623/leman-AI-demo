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
 * AI 同步记录 DO。
 */
@TableName("ai_sync_record")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiSyncRecordDO {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    @TableField("tenant_id")
    private Long tenantId;
    @TableField("sync_job_id")
    private Long syncJobId;
    @TableField("knowledge_base_id")
    private Long knowledgeBaseId;
    @TableField("data_source_id")
    private Long dataSourceId;
    @TableField("document_id")
    private Long documentId;
    @TableField("source_uri")
    private String sourceUri;
    @TableField("action_type")
    private String actionType;
    @TableField("status")
    private Integer status;
    @TableField("start_time")
    private LocalDateTime startTime;
    @TableField("end_time")
    private LocalDateTime endTime;
    @TableField("error_message")
    private String errorMessage;
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
