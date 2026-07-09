package cn.iocoder.yudao.module.ai.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Lead agent collected customer.
 */
@TableName("ai_lead_customer")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeadCustomerDO {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    @TableField("tenant_id")
    private Long tenantId;
    @TableField("run_id")
    private String runId;
    @TableField("company_name")
    private String companyName;
    @TableField("country")
    private String country;
    @TableField("website")
    private String website;
    @TableField("domain")
    private String domain;
    @TableField("emails_json")
    private String emailsJson;
    @TableField("best_email")
    private String bestEmail;
    @TableField("email_type")
    private String emailType;
    @TableField("phone")
    private String phone;
    @TableField("contact_page")
    private String contactPage;
    @TableField("about_page")
    private String aboutPage;
    @TableField("main_products_json")
    private String mainProductsJson;
    @TableField("is_target")
    private Boolean target;
    @TableField("matched_category")
    private String matchedCategory;
    @TableField("target_customer_type")
    private String targetCustomerType;
    @TableField("score")
    private Integer score;
    @TableField("score_reason")
    private String scoreReason;
    @TableField("source_url")
    private String sourceUrl;
    @TableField("collected_at")
    private LocalDateTime collectedAt;
    @TableField("compliance_note")
    private String complianceNote;
    @TableField("development_email_subject")
    private String developmentEmailSubject;
    @TableField("development_email_body")
    private String developmentEmailBody;
    @TableField("duplicate_status")
    private String duplicateStatus;
    @TableField("crawl_errors_json")
    private String crawlErrorsJson;
    @TableField("analysis_provider")
    private String analysisProvider;
    @TableField("ai_review_error")
    private String aiReviewError;
    @TableField("social_profiles_json")
    private String socialProfilesJson;
    @TableField("social_activity_summary")
    private String socialActivitySummary;
    @TableField("social_verification_score")
    private Integer socialVerificationScore;
    @TableField("social_verification_reason")
    private String socialVerificationReason;
    @TableField("external_appearance_count")
    private Integer externalAppearanceCount;
    @TableField("external_appearance_urls_json")
    private String externalAppearanceUrlsJson;
    @TableField("demo_status")
    private String demoStatus;
    @TableField("creator")
    private String creator;
    @TableField("create_time")
    private LocalDateTime createTime;
    @TableField("updater")
    private String updater;
    @TableField("update_time")
    private LocalDateTime updateTime;
    @TableLogic
    @TableField("deleted")
    private Boolean deleted;

}
