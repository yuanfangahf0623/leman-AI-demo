package cn.iocoder.yudao.module.ai.controller.admin.document.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * AI 文档分页请求。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class AiDocumentPageReqVO extends PageParam {

    private Long knowledgeBaseId;

    private Integer parseStatus;

    private Integer embeddingStatus;

    private String title;

}
