package cn.iocoder.yudao.module.ai.controller.admin.lead.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Lead crawl job page request.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class LeadCrawlJobPageReqVO extends PageParam {

    private String runId;
    private String status;
    private String categoryCode;

}
