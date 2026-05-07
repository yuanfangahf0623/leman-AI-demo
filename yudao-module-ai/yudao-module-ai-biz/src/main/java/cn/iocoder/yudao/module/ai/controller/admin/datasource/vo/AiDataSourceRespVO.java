package cn.iocoder.yudao.module.ai.controller.admin.datasource.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI data source response.
 */
@Data
public class AiDataSourceRespVO {

    private Long id;
    private Long knowledgeBaseId;
    private String name;
    private String sourceType;
    private String syncMode;
    private String configJson;
    private Boolean syncEnabled;
    private LocalDateTime lastSyncTime;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

}
