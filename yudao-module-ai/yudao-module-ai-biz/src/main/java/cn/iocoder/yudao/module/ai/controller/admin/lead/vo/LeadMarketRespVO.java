package cn.iocoder.yudao.module.ai.controller.admin.lead.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Lead market response.
 */
@Data
public class LeadMarketRespVO {

    private Long id;
    private String categoryCode;
    private String categoryName;
    private Integer weight;
    private Boolean enabled;
    private List<String> countries;
    private List<String> keywords;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

}
