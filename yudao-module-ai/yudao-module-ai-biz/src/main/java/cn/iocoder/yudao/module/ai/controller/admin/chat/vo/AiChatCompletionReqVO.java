package cn.iocoder.yudao.module.ai.controller.admin.chat.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * AI 问答请求。
 */
@Data
public class AiChatCompletionReqVO {

    @NotNull(message = "知识库编号不能为空")
    private Long knowledgeBaseId;

    private Long conversationId;

    @NotBlank(message = "问题不能为空")
    @Size(max = 2000, message = "问题不能超过 2000 个字符")
    private String question;

    /**
     * 第一阶段只允许 false 或空。
     */
    private Boolean stream = false;

}
