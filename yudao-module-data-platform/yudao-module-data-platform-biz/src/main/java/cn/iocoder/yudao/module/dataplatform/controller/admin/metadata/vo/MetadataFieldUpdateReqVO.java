package cn.iocoder.yudao.module.dataplatform.controller.admin.metadata.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MetadataFieldUpdateReqVO {
    @NotNull(message = "元数据字段编号不能为空")
    private Long id;
    @NotBlank(message = "业务中文名不能为空")
    @Size(max = 128, message = "业务中文名不能超过 128 个字符")
    private String businessName;
    @Size(max = 1000, message = "业务口径不能超过 1000 个字符")
    private String description;
    @Size(max = 64, message = "数据分类不能超过 64 个字符")
    private String classification;
    @NotBlank(message = "敏感等级不能为空")
    @Pattern(regexp = "PUBLIC|INTERNAL|SENSITIVE|RESTRICTED", message = "敏感等级非法")
    private String sensitivityLevel;
    private Boolean incrementalCandidate;
}
