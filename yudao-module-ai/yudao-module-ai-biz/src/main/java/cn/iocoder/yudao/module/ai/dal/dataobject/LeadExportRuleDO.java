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
 * Lead agent export rule.
 */
@TableName("ai_lead_export_rule")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeadExportRuleDO {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    @TableField("tenant_id")
    private Long tenantId;
    @TableField("min_score")
    private Integer minScore;
    @TableField("include_target_only")
    private Boolean includeTargetOnly;
    @TableField("require_email")
    private Boolean requireEmail;
    @TableField("allowed_grades")
    private String allowedGrades;
    @TableField("include_possible_duplicates")
    private Boolean includePossibleDuplicates;
    @TableField("write_rejected_file")
    private Boolean writeRejectedFile;
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
