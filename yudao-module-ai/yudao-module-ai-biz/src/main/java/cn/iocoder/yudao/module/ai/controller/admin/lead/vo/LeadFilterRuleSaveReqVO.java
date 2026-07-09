package cn.iocoder.yudao.module.ai.controller.admin.lead.vo;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * Lead filter rule save request.
 */
@Data
public class LeadFilterRuleSaveReqVO {

    private List<@Size(max = 128, message = "域名关键词不能超过 128 个字符") String> blockedDomainKeywords;
    private List<@Size(max = 32, message = "文件后缀不能超过 32 个字符") String> blockedFileExtensions;

}
