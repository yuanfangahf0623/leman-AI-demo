package cn.iocoder.yudao.module.ai.controller.admin.datasource.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI data source raw record page request.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AiDataSourceRawRecordPageReqVO extends PageParam {

    private Long knowledgeBaseId;
    private Long dataSourceId;
    private Long syncJobId;
    private String provider;
    private String moduleName;
    private String objectType;
    private String externalId;

}
