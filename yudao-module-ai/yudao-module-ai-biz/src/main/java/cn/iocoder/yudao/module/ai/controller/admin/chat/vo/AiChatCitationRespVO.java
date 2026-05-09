package cn.iocoder.yudao.module.ai.controller.admin.chat.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI 问答引用来源响应。
 */
@Data
public class AiChatCitationRespVO {

    private Long id;

    private Long messageId;

    private Long knowledgeBaseId;

    private Long documentId;

    private Long chunkId;

    private String documentTitle;

    private BigDecimal score;

    private Integer sortOrder;

    private String contentSnapshot;

    private LocalDateTime createTime;

}
