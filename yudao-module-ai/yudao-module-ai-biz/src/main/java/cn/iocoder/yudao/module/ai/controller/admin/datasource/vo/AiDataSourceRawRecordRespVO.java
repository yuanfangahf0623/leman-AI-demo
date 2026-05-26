package cn.iocoder.yudao.module.ai.controller.admin.datasource.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI data source raw record response.
 */
@Data
public class AiDataSourceRawRecordRespVO {

    private Long id;
    private Long knowledgeBaseId;
    private Long dataSourceId;
    private Long syncJobId;
    private String provider;
    private String moduleName;
    private String objectType;
    private String externalId;
    private String sourceUri;
    private String payloadJson;
    private String payloadHash;
    private LocalDateTime recordTime;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

}
