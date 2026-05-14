package cn.iocoder.yudao.module.ai.controller.admin.chat.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
     * 可选召回数量。为空时使用知识库配置。
     */
    @Min(value = 1, message = "召回数量不能小于 1")
    @Max(value = 100, message = "召回数量不能大于 100")
    private Integer topK;

    /**
     * 可选相似度阈值。为空时使用知识库配置。
     */
    @DecimalMin(value = "0.0", message = "相似度阈值不能小于 0")
    @DecimalMax(value = "1.0", message = "相似度阈值不能大于 1")
    private Double scoreThreshold;

    /**
     * 第一阶段只允许 false 或空。
     */
    private Boolean stream = false;

    /**
     * Whether this request may supplement knowledge-base hits with online search results.
     */
    private Boolean webSearchEnabled = false;

}
