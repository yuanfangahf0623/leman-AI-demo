package cn.iocoder.yudao.module.ai.controller.admin.lead.vo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * Lead export rule save request.
 */
@Data
public class LeadExportRuleSaveReqVO {

    @Min(value = 0, message = "最低分不能小于 0")
    @Max(value = 100, message = "最低分不能大于 100")
    private Integer minScore = 0;

    private Boolean includeTargetOnly = false;
    private Boolean requireEmail = false;

    @NotEmpty(message = "至少需要一个允许导出的等级")
    private List<String> allowedGrades = List.of("A", "B", "C", "D");

    private Boolean includePossibleDuplicates = true;
    private Boolean writeRejectedFile = true;

}
