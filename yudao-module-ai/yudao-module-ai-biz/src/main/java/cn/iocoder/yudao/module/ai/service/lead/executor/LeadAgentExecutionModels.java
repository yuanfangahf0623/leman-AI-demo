package cn.iocoder.yudao.module.ai.service.lead.executor;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Internal DTOs used by the lead-agent execution chain.
 */
public final class LeadAgentExecutionModels {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";

    private LeadAgentExecutionModels() {
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RunOptions {
        private Long tenantId;
        private Long jobId;
        private String runId;
        private String categoryCode;
        private String country;
        private int maxResults;
        private int maxPagesPerSite;
        private int crawlTimeoutSeconds;
        private String searchProvider;
        private boolean enableAiReview;
        private boolean skipSocialVerification;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SearchJob {
        private String category;
        private String country;
        private String keyword;
        private int weight;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CandidateRecord {
        private String url;
        private String category;
        private String country;
        private String keyword;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SearchEvidence {
        private String url;
        private String title;
        private String snippet;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CrawledPage {
        private String url;
        private String html;
        private String text;
        private String title;
        private String pageType;
        private String error;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CrawlResult {
        private String startUrl;
        private String finalUrl;
        private String domain;
        @Builder.Default
        private List<CrawledPage> pages = new ArrayList<>();
        @Builder.Default
        private List<String> errors = new ArrayList<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExtractionResult {
        private String companyName;
        @Builder.Default
        private List<String> emails = new ArrayList<>();
        @Builder.Default
        private Map<String, String> emailSources = Map.of();
        private String bestEmail;
        private String emailType;
        private String phone;
        private String contactPage;
        private String aboutPage;
        @Builder.Default
        private List<String> mainProducts = new ArrayList<>();
        private String sourceUrl;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClassificationResult {
        private boolean target;
        private String targetCustomerType;
        private String matchedCategory;
        private String reason;
        private boolean productRelevanceHigh;
        private boolean hasB2bSignal;
        private boolean hasAddressOrImprint;
        private boolean largePlatformOrAggregator;
        private boolean productUnrelated;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SocialProfile {
        private String platform;
        private String url;
        private String source;
        private String sourceUrl;
        private boolean matchedCompany;
        private boolean matchedDomain;
        private String activityStatus;
        private String evidence;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SocialVerificationResult {
        @Builder.Default
        private List<SocialProfile> profiles = new ArrayList<>();
        private int externalAppearanceCount;
        @Builder.Default
        private List<String> externalAppearanceUrls = new ArrayList<>();
        private int confidenceScore;
        private String confidenceReason;
        private String activitySummary;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LeadRecord {
        private String companyName;
        private String country;
        private String website;
        private String domain;
        @Builder.Default
        private List<String> emails = new ArrayList<>();
        private String bestEmail;
        private String emailType;
        private String phone;
        private String contactPage;
        private String aboutPage;
        @Builder.Default
        private List<String> mainProducts = new ArrayList<>();
        private Boolean target;
        private String matchedCategory;
        private String targetCustomerType;
        private int score;
        private String scoreReason;
        private String sourceUrl;
        private String collectedAt;
        private String complianceNote;
        private String developmentEmailSubject;
        private String developmentEmailBody;
        private String duplicateStatus;
        @Builder.Default
        private List<String> crawlErrors = new ArrayList<>();
        private String analysisProvider;
        private String aiReviewError;
        @Builder.Default
        private List<SocialProfile> socialProfiles = new ArrayList<>();
        private String socialActivitySummary;
        private int socialVerificationScore;
        private String socialVerificationReason;
        private int externalAppearanceCount;
        @Builder.Default
        private List<String> externalAppearanceUrls = new ArrayList<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProcessedLead {
        private CandidateRecord candidate;
        private CrawlResult crawlResult;
        private ClassificationResult classification;
        private LeadRecord lead;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExportSplit {
        private List<LeadRecord> included;
        private List<LeadRecord> rejected;
    }

}
