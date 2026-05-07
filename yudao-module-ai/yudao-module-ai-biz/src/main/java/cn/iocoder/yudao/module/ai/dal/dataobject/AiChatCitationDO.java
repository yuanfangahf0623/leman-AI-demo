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

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI 引用来源 DO。
 */
@TableName("ai_chat_citation")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiChatCitationDO {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    @TableField("tenant_id")
    private Long tenantId;
    @TableField("message_id")
    private Long messageId;
    @TableField("knowledge_base_id")
    private Long knowledgeBaseId;
    @TableField("document_id")
    private Long documentId;
    @TableField("chunk_id")
    private Long chunkId;
    @TableField("score")
    private BigDecimal score;
    @TableField("sort_order")
    private Integer sortOrder;
    @TableField("quote_text")
    private String quoteText;
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
