package cn.iocoder.yudao.module.ai.service.rag;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * RAG 问答请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagChatRequest {

    private Long knowledgeBaseId;

    private Long conversationId;

    private String question;

    private Integer topK;

    private Double scoreThreshold;

    private Boolean webSearchEnabled;

}
