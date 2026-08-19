package cn.iocoder.yudao.module.dataplatform.controller.admin.warehouse.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WarehouseColumnRespVO {
    private String name;
    private String type;
    private boolean nullable;
    private String key;
    private String defaultValue;
    private String comment;
}
