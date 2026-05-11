package cn.iocoder.yudao.module.ai.controller.admin.chat.vo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * AI 问答会话置顶请求。
 */
@Data
public class AiChatConversationPinReqVO {

    @NotNull(message = "会话编号不能为空")
    private Long id;

    @NotNull(message = "置顶状态不能为空")
    private Boolean pinned;

}
