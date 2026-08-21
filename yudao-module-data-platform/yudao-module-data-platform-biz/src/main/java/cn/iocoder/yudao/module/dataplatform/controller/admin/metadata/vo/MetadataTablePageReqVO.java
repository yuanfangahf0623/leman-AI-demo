package cn.iocoder.yudao.module.dataplatform.controller.admin.metadata.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class MetadataTablePageReqVO extends PageParam {
    private Long dataSourceId;
    private String keyword;
    private String businessDomain;
    private String definitionStatus;
}
