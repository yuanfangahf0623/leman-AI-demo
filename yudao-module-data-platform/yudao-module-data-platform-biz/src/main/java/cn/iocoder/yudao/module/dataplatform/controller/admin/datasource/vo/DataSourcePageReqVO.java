package cn.iocoder.yudao.module.dataplatform.controller.admin.datasource.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class DataSourcePageReqVO extends PageParam {
    private String name;
    private String type;
    private Integer status;
}
