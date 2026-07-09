package cn.iocoder.yudao.module.ai.controller.admin.lead.vo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * Lead crawl run create request.
 */
@Data
public class LeadRunCreateReqVO {

    private String categoryCode;
    private String country;
    private String searchProvider;
    private Boolean enableAiReview;
    private Boolean skipSocialVerification;

    @Min(value = 1, message = "maxResults must be at least 1")
    @Max(value = 500, message = "maxResults must be at most 500")
    private Integer maxResults = 50;

    @Min(value = 1, message = "maxPagesPerSite must be at least 1")
    @Max(value = 10, message = "maxPagesPerSite must be at most 10")
    private Integer maxPagesPerSite = 5;

    @Min(value = 5, message = "crawlTimeoutSeconds must be at least 5")
    @Max(value = 60, message = "crawlTimeoutSeconds must be at most 60")
    private Integer crawlTimeoutSeconds = 15;

}
