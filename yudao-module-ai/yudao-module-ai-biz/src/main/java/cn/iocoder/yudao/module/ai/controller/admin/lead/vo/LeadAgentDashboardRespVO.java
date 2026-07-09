package cn.iocoder.yudao.module.ai.controller.admin.lead.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Lead agent dashboard response.
 */
@Data
public class LeadAgentDashboardRespVO {

    private LocalDateTime generatedAt;
    private Long customerTotal;
    private Long targetCount;
    private Long withEmailCount;
    private Long historyTotal;
    private Long marketCategoryCount;
    private Long countryCount;
    private Long keywordCount;
    private Long filterBlockedDomainCount;
    private Long filterBlockedFileExtensionCount;
    private LocalDateTime latestCollectedAt;
    private Map<String, Long> gradeCounts;
    private Map<String, Long> categoryCounts;
    private Map<String, Long> providerCounts;

}
