package cn.iocoder.yudao.module.dataplatform.controller.admin.metadata.vo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MetadataTableUpdateReqVO {
    @NotNull(message = "元数据表编号不能为空")
    private Long id;
    @Size(max = 128, message = "业务中文名不能超过 128 个字符")
    private String businessName;
    @Size(max = 64, message = "业务域不能超过 64 个字符")
    private String businessDomain;
    @Size(max = 1000, message = "业务说明不能超过 1000 个字符")
    private String description;
}
