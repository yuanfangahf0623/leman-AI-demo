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
 * AI 文档切片 DO。
 */
@TableName("ai_document_chunk")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiDocumentChunkDO {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    @TableField("tenant_id")
    private Long tenantId;
    @TableField("knowledge_base_id")
    private Long knowledgeBaseId;
    @TableField("document_id")
    private Long documentId;
    @TableField("chunk_index")
    private Integer chunkIndex;
    @TableField("content")
    private String content;
    @TableField("content_hash")
    private String contentHash;
    @TableField("token_count")
    private Integer tokenCount;
    @TableField("vector_id")
    private String vectorId;
    @TableField("metadata_json")
    private String metadataJson;
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
