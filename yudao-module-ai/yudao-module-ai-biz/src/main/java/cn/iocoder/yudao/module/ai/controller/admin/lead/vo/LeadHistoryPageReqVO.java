package cn.iocoder.yudao.module.ai.controller.admin.lead.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Lead crawl history page request.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class LeadHistoryPageReqVO extends PageParam {

    private String runId;
    private String domain;
    private String searchCategory;
    private String crawlStatus;

}
