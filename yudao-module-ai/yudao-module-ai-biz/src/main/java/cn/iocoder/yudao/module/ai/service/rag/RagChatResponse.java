package cn.iocoder.yudao.module.ai.service.rag;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * RAG 问答响应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagChatResponse {

    private Long conversationId;

    private Long userMessageId;

    private Long assistantMessageId;

    private String answer;

    private Boolean noContext;

    private List<RagChatCitation> citations;

}
