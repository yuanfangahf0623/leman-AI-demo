package cn.iocoder.yudao.module.ai.controller.admin.chat.vo;

import lombok.Data;

/**
 * AI 问答引用来源响应。
 */
@Data
public class AiChatCompletionCitationRespVO {

    private Long documentId;

    private Long chunkId;

    private Integer chunkNo;

    private String documentTitle;

    private Double score;

    private String quoteText;

}
