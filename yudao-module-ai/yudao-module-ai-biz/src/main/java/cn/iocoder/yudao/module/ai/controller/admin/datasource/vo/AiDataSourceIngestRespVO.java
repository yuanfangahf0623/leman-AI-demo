package cn.iocoder.yudao.module.ai.controller.admin.datasource.vo;

import lombok.Builder;
import lombok.Data;

/**
 * API data source ingest response.
 */
@Data
@Builder
public class AiDataSourceIngestRespVO {

    private Long documentId;

    private Long dataSourceId;

    private Long knowledgeBaseId;

    private String sourceUri;

    private String action;

}
