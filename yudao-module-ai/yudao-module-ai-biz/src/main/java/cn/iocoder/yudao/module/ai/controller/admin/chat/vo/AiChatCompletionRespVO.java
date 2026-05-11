package cn.iocoder.yudao.module.ai.controller.admin.chat.vo;

import lombok.Data;

import java.util.List;

/**
 * AI 问答响应。
 */
@Data
public class AiChatCompletionRespVO {

    private Long conversationId;

    private Long userMessageId;

    private Long assistantMessageId;

    private String answer;

    private Boolean noContext;

    private String debugInfo;

    private List<AiChatCompletionCitationRespVO> citations;

}
