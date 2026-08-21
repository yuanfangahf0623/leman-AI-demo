package cn.iocoder.yudao.module.dataplatform.controller.admin.sync.vo;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SyncFieldMappingVO {

    @Size(max = 128)
    private String sourceField;

    @Size(max = 128)
    private String sourceType;

    @Size(max = 128)
    private String targetField;

    @Size(max = 128)
    private String targetType;

    private Boolean enabled;

    private Boolean required;

    @Size(max = 1000)
    private String defaultValue;

    @Pattern(regexp = "NONE|TRIM|HEX", message = "字段转换仅支持 NONE、TRIM 或 HEX")
    private String transform;
}
