package cn.iocoder.yudao.module.ai.controller.admin.knowledge.vo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * AI knowledge base request fields.
 */
@Data
public class AiKnowledgeBaseVO {

    @NotBlank(message = "知识库名称不能为空")
    @Size(max = 128, message = "知识库名称不能超过 128 个字符")
    private String name;

    @NotBlank(message = "知识库编码不能为空")
    @Size(max = 64, message = "知识库编码不能超过 64 个字符")
    private String code;

    @Size(max = 512, message = "知识库描述不能超过 512 个字符")
    private String description;

    private Integer status;

    @NotBlank(message = "向量库类型不能为空")
    @Size(max = 32, message = "向量库类型不能超过 32 个字符")
    private String vectorStoreType;

    @Size(max = 128, message = "Embedding 模型不能超过 128 个字符")
    private String embeddingModel;

    @NotNull(message = "切片大小不能为空")
    @Min(value = 1, message = "切片大小必须大于 0")
    @Max(value = 100000, message = "切片大小不能超过 100000")
    private Integer chunkSize;

    @NotNull(message = "切片重叠大小不能为空")
    @Min(value = 0, message = "切片重叠大小不能小于 0")
    @Max(value = 100000, message = "切片重叠大小不能超过 100000")
    private Integer chunkOverlap;

    @NotNull(message = "召回数量不能为空")
    @Min(value = 1, message = "召回数量必须大于 0")
    @Max(value = 100, message = "召回数量不能超过 100")
    private Integer topK;

}
