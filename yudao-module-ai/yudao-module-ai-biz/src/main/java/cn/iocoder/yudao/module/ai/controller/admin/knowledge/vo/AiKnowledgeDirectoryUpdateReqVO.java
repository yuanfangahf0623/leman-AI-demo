package cn.iocoder.yudao.module.ai.controller.admin.knowledge.vo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * AI 知识库目录更新请求。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class AiKnowledgeDirectoryUpdateReqVO extends AiKnowledgeDirectoryBaseVO {

    @NotNull(message = "目录编号不能为空")
    private Long id;

}
