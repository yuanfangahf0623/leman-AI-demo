package cn.iocoder.yudao.module.ai.controller.admin.knowledge.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * AI knowledge page request.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class AiKnowledgePageReqVO extends PageParam {

    private String name;

    private String code;

    private Integer status;

    private String vectorStoreType;

}
