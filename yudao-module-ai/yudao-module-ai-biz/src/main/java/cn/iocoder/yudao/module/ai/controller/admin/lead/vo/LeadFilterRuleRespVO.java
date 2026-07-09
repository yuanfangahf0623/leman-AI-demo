package cn.iocoder.yudao.module.ai.controller.admin.lead.vo;

import lombok.Data;

import java.util.List;

/**
 * Lead filter rule response.
 */
@Data
public class LeadFilterRuleRespVO {

    private List<String> blockedDomainKeywords;
    private List<String> blockedFileExtensions;

}
