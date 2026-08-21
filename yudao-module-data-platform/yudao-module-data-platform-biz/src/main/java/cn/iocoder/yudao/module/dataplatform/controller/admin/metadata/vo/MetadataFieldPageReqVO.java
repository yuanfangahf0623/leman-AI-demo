package cn.iocoder.yudao.module.dataplatform.controller.admin.metadata.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class MetadataFieldPageReqVO extends PageParam {
    @NotNull(message = "元数据表编号不能为空")
    private Long metadataTableId;
    private String keyword;
    private String definitionStatus;
    private String sensitivityLevel;
}
