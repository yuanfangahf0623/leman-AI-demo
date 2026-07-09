package cn.iocoder.yudao.module.ai.controller.admin.lead.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Lead crawl history response.
 */
@Data
public class LeadHistoryRespVO {

    private Long id;
    private String runId;
    private String searchProvider;
    private String searchCategory;
    private String searchCountry;
    private String searchKeyword;
    private String website;
    private String domain;
    private String crawlStatus;
    private Integer pagesCrawled;
    private List<String> crawledPages;
    private List<String> crawlErrors;
    private String classificationReason;
    private Boolean target;
    private Integer score;
    private Long customerId;
    private Map<String, Object> raw;
    private LocalDateTime collectedAt;

}
