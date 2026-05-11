package cn.iocoder.yudao.module.ai.controller.admin.chat.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 问答会话响应。
 */
@Data
public class AiChatConversationRespVO {

    private Long id;

    private Long knowledgeBaseId;

    private Long userId;

    private Long departmentId;

    private String title;

    private Integer status;

    private Boolean pinned;

    private LocalDateTime pinnedTime;

    private LocalDateTime lastMessageTime;

    private LocalDateTime createTime;

}
