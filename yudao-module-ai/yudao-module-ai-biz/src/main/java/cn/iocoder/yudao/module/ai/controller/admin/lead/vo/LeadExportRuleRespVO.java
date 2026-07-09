package cn.iocoder.yudao.module.ai.controller.admin.lead.vo;

import lombok.Data;

import java.util.List;

/**
 * Lead export rule response.
 */
@Data
public class LeadExportRuleRespVO {

    private Integer minScore;
    private Boolean includeTargetOnly;
    private Boolean requireEmail;
    private List<String> allowedGrades;
    private Boolean includePossibleDuplicates;
    private Boolean writeRejectedFile;

}
