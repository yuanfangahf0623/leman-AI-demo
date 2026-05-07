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
 * AI 知识库 DO。
 */
@TableName("ai_knowledge_base")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiKnowledgeBaseDO {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    @TableField("tenant_id")
    private Long tenantId;
    @TableField("name")
    private String name;
    @TableField("code")
    private String code;
    @TableField("description")
    private String description;
    @TableField("status")
    private Integer status;
    @TableField("vector_store_type")
    private String vectorStoreType;
    @TableField("embedding_model")
    private String embeddingModel;
    @TableField("document_count")
    private Integer documentCount;
    @TableField("chunk_count")
    private Integer chunkCount;
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
