package cn.iocoder.yudao.module.ai.controller.admin.chat.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * AI 问答会话重命名请求。
 */
@Data
public class AiChatConversationRenameReqVO {

    @NotNull(message = "会话编号不能为空")
    private Long id;

    @NotBlank(message = "会话名称不能为空")
    @Size(max = 64, message = "会话名称不能超过 64 个字符")
    private String title;

}
