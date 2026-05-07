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
 * AI 文档 DO。
 */
@TableName("ai_document")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiDocumentDO {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    @TableField("tenant_id")
    private Long tenantId;
    @TableField("knowledge_base_id")
    private Long knowledgeBaseId;
    @TableField("data_source_id")
    private Long dataSourceId;
    @TableField("title")
    private String title;
    @TableField("file_name")
    private String fileName;
    @TableField("file_type")
    private String fileType;
    @TableField("file_size")
    private Long fileSize;
    @TableField("object_key")
    private String objectKey;
    @TableField("source_uri")
    private String sourceUri;
    @TableField("content_hash")
    private String contentHash;
    @TableField("parse_status")
    private Integer parseStatus;
    @TableField("embedding_status")
    private Integer embeddingStatus;
    @TableField("chunk_count")
    private Integer chunkCount;
    @TableField("token_count")
    private Integer tokenCount;
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
