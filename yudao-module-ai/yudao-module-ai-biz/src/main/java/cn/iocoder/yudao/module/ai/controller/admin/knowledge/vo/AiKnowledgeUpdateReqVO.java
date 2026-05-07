package cn.iocoder.yudao.module.ai.controller.admin.knowledge.vo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * AI knowledge update request.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class AiKnowledgeUpdateReqVO extends AiKnowledgeBaseVO {

    @NotNull(message = "知识库编号不能为空")
    private Long id;

}
