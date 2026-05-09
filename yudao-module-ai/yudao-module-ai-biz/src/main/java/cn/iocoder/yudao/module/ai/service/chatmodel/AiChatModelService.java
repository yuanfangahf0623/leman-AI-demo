package cn.iocoder.yudao.module.ai.service.chatmodel;

/**
 * AI 聊天模型服务抽象。
 */
public interface AiChatModelService {

    AiChatModelResponse chat(AiChatModelRequest request);

}
