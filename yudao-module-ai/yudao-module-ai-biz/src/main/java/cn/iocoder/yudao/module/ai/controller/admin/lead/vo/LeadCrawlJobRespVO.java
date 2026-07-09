package cn.iocoder.yudao.module.ai.controller.admin.lead.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Lead crawl job response.
 */
@Data
public class LeadCrawlJobRespVO {

    private Long id;
    private String runId;
    private String categoryCode;
    private String country;
    private Integer maxResults;
    private Integer maxPagesPerSite;
    private Integer crawlTimeoutSeconds;
    private String searchProvider;
    private String analysisProvider;
    private Boolean skipSocialVerification;
    private Boolean enableAiReview;
    private String status;
    private Integer totalCandidates;
    private Integer crawledCount;
    private Integer leadCount;
    private Integer exportedCount;
    private Integer rejectedCount;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

}
