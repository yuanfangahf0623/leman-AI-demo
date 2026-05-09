package cn.iocoder.yudao.module.ai.service.chatmodel;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 聊天模型请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiChatModelRequest {

    /**
     * 模型名称。为空时由实现使用默认配置。
     */
    private String model;

    /**
     * 对话消息列表。
     */
    private List<AiChatModelMessage> messages;

    /**
     * 系统提示词。未显式传 messages 时，会作为 system 消息发送。
     */
    private String systemPrompt;

    /**
     * 用户提示词。未显式传 messages 时，会作为 user 消息发送。
     */
    private String userPrompt;

    /**
     * 采样温度。
     */
    private Double temperature;

    /**
     * 最大输出 Token 数。
     */
    private Integer maxTokens;

    /**
     * 业务元数据，供 RAG 编排层透传上下文。
     */
    private Map<String, Object> metadata;

}
