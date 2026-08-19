package cn.iocoder.yudao.module.dataplatform.controller.admin.sync.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class SyncJobPageReqVO extends PageParam {
    private String name;
    private String syncMode;
    private Integer status;
}
