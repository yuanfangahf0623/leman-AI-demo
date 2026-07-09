package cn.iocoder.yudao.module.ai.controller.admin.lead.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Lead market page request.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class LeadMarketPageReqVO extends PageParam {

    private String categoryCode;
    private String categoryName;
    private Boolean enabled;

}
