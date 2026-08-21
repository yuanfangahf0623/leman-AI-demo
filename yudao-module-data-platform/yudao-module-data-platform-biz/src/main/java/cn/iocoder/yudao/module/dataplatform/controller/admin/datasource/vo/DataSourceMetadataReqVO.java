package cn.iocoder.yudao.module.dataplatform.controller.admin.datasource.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DataSourceMetadataReqVO {

    @NotNull
    private Long dataSourceId;

    @NotBlank
    @Size(max = 8000)
    private String sourceSql;
}
