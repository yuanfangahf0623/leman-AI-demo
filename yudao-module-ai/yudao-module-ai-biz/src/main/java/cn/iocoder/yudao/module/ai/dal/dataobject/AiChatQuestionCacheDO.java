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
 * AI 问答问题缓存 DO。
 */
@TableName("ai_chat_question_cache")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiChatQuestionCacheDO {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    @TableField("tenant_id")
    private Long tenantId;
    @TableField("department_id")
    private Long departmentId;
    @TableField("knowledge_base_id")
    private Long knowledgeBaseId;
    @TableField("user_id")
    private Long userId;
    @TableField("question")
    private String question;
    @TableField("normalized_question")
    private String normalizedQuestion;
    @TableField("question_hash")
    private String questionHash;
    @TableField("answer")
    private String answer;
    @TableField("model")
    private String model;
    @TableField("prompt_tokens")
    private Integer promptTokens;
    @TableField("completion_tokens")
    private Integer completionTokens;
    @TableField("total_tokens")
    private Integer totalTokens;
    @TableField("latency_ms")
    private Long latencyMs;
    @TableField("citation_snapshot_json")
    private String citationSnapshotJson;
    @TableField("hit_count")
    private Integer hitCount;
    @TableField("last_hit_time")
    private LocalDateTime lastHitTime;
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
