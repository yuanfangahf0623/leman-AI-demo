package cn.iocoder.yudao.module.ai.controller.admin.chat.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 问答消息响应。
 */
@Data
public class AiChatMessageRespVO {

    private Long id;

    private Long conversationId;

    private String role;

    private String content;

    private String model;

    private Integer promptTokens;

    private Integer completionTokens;

    private Integer totalTokens;

    private Long latencyMs;

    private Integer status;

    private String errorMessage;

    private LocalDateTime createTime;

}
