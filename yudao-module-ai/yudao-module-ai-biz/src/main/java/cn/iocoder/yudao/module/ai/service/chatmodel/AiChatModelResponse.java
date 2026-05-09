package cn.iocoder.yudao.module.ai.service.chatmodel;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 聊天模型响应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiChatModelResponse {

    /**
     * 模型名称。
     */
    private String model;

    /**
     * 模型输出内容。
     */
    private String content;

    /**
     * 结束原因。
     */
    private String finishReason;

    private Integer promptTokens;

    private Integer completionTokens;

    private Integer totalTokens;

    /**
     * 扩展元数据。
     */
    private Map<String, Object> metadata;

}
