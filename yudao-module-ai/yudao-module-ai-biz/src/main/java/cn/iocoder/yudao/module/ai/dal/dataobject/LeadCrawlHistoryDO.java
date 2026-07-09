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
 * Lead agent crawl history.
 */
@TableName("ai_lead_crawl_history")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeadCrawlHistoryDO {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    @TableField("tenant_id")
    private Long tenantId;
    @TableField("run_id")
    private String runId;
    @TableField("search_provider")
    private String searchProvider;
    @TableField("search_category")
    private String searchCategory;
    @TableField("search_country")
    private String searchCountry;
    @TableField("search_keyword")
    private String searchKeyword;
    @TableField("website")
    private String website;
    @TableField("domain")
    private String domain;
    @TableField("crawl_status")
    private String crawlStatus;
    @TableField("pages_crawled")
    private Integer pagesCrawled;
    @TableField("crawled_pages_json")
    private String crawledPagesJson;
    @TableField("crawl_errors_json")
    private String crawlErrorsJson;
    @TableField("classification_reason")
    private String classificationReason;
    @TableField("is_target")
    private Boolean target;
    @TableField("score")
    private Integer score;
    @TableField("customer_id")
    private Long customerId;
    @TableField("raw_json")
    private String rawJson;
    @TableField("collected_at")
    private LocalDateTime collectedAt;
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
