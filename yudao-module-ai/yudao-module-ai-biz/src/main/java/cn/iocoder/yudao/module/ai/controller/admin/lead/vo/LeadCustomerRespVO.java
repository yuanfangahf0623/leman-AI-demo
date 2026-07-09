package cn.iocoder.yudao.module.ai.controller.admin.lead.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Lead customer response.
 */
@Data
public class LeadCustomerRespVO {

    private Long id;
    private String runId;
    private String companyName;
    private String country;
    private String website;
    private String domain;
    private List<String> emails;
    private String bestEmail;
    private String emailType;
    private String phone;
    private String contactPage;
    private String aboutPage;
    private List<String> mainProducts;
    private Boolean target;
    private String matchedCategory;
    private String targetCustomerType;
    private Integer score;
    private String grade;
    private String scoreReason;
    private String sourceUrl;
    private LocalDateTime collectedAt;
    private String complianceNote;
    private String developmentEmailSubject;
    private String developmentEmailBody;
    private String duplicateStatus;
    private List<String> crawlErrors;
    private String analysisProvider;
    private String aiReviewError;
    private List<Map<String, Object>> socialProfiles;
    private String socialActivitySummary;
    private Integer socialVerificationScore;
    private String socialVerificationReason;
    private Integer externalAppearanceCount;
    private List<String> externalAppearanceUrls;
    private String demoStatus;

}
