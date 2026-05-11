package cn.iocoder.yudao.module.ai.controller.admin.knowledge.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * AI 知识库目录基础 VO。
 */
@Data
public class AiKnowledgeDirectoryBaseVO {

    @NotNull(message = "知识库编号不能为空")
    private Long knowledgeBaseId;

    private Long parentId;

    @NotBlank(message = "目录名称不能为空")
    @Size(max = 128, message = "目录名称长度不能超过 128 个字符")
    private String name;

    private Integer sort;

    private Integer status;

}
