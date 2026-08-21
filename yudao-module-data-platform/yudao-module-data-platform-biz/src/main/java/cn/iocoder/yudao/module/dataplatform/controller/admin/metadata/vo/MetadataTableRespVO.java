package cn.iocoder.yudao.module.dataplatform.controller.admin.metadata.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MetadataTableRespVO {
    private Long id;
    private Long dataSourceId;
    private String dataSourceName;
    private String sourceSchema;
    private String sourceTable;
    private String businessName;
    private String businessDomain;
    private String description;
    private String targetDatabase;
    private String targetTable;
    private Integer fieldCount;
    private Integer commentedFieldCount;
    private Long confirmedFieldCount;
    private String definitionStatus;
    private LocalDateTime lastScanTime;
}
