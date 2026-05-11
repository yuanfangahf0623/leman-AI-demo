package cn.iocoder.yudao.module.ai.controller.admin.knowledge.vo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * AI 知识库目录列表请求。
 */
@Data
public class AiKnowledgeDirectoryListReqVO {

    @NotNull(message = "知识库编号不能为空")
    private Long knowledgeBaseId;

}
