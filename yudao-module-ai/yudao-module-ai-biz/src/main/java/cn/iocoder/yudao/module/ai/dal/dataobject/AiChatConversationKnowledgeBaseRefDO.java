package cn.iocoder.yudao.module.ai.dal.dataobject;

import lombok.Data;

/**
 * AI chat conversation display knowledge base reference.
 */
@Data
public class AiChatConversationKnowledgeBaseRefDO {

    private Long conversationId;

    private Long knowledgeBaseId;

    private String knowledgeBaseName;

}
