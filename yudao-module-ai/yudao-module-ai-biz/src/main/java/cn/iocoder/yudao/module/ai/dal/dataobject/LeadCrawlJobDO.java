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
 * Lead agent crawl job.
 */
@TableName("ai_lead_crawl_job")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeadCrawlJobDO {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    @TableField("tenant_id")
    private Long tenantId;
    @TableField("run_id")
    private String runId;
    @TableField("category_code")
    private String categoryCode;
    @TableField("country")
    private String country;
    @TableField("max_results")
    private Integer maxResults;
    @TableField("max_pages_per_site")
    private Integer maxPagesPerSite;
    @TableField("crawl_timeout_seconds")
    private Integer crawlTimeoutSeconds;
    @TableField("search_provider")
    private String searchProvider;
    @TableField("analysis_provider")
    private String analysisProvider;
    @TableField("skip_social_verification")
    private Boolean skipSocialVerification;
    @TableField("enable_ai_review")
    private Boolean enableAiReview;
    @TableField("status")
    private String status;
    @TableField("total_candidates")
    private Integer totalCandidates;
    @TableField("crawled_count")
    private Integer crawledCount;
    @TableField("lead_count")
    private Integer leadCount;
    @TableField("exported_count")
    private Integer exportedCount;
    @TableField("rejected_count")
    private Integer rejectedCount;
    @TableField("error_message")
    private String errorMessage;
    @TableField("started_at")
    private LocalDateTime startedAt;
    @TableField("finished_at")
    private LocalDateTime finishedAt;
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
