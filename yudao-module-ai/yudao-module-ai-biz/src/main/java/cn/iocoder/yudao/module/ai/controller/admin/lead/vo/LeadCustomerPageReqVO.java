package cn.iocoder.yudao.module.ai.controller.admin.lead.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Lead customer page request.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class LeadCustomerPageReqVO extends PageParam {

    private String companyName;
    private String domain;
    private String country;
    private String matchedCategory;
    private Boolean target;
    private Integer minScore;
    private Boolean hasEmail;

}
