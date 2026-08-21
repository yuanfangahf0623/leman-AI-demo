package cn.iocoder.yudao.module.dataplatform.controller.admin.datasource.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DataSourceColumnRespVO {

    private String name;
    private String label;
    private String type;
    private Integer jdbcType;
    private Boolean nullable;
    private Integer ordinal;
}
