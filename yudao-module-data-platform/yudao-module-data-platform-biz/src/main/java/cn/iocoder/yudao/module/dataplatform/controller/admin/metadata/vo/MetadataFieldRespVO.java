package cn.iocoder.yudao.module.dataplatform.controller.admin.metadata.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MetadataFieldRespVO {
    private Long id;
    private Long metadataTableId;
    private String sourceColumn;
    private String targetColumn;
    private String dataType;
    private Integer jdbcType;
    private Integer columnSize;
    private Integer decimalDigits;
    private Boolean nullable;
    private Boolean primaryKey;
    private Integer ordinalPosition;
    private String sourceComment;
    private String businessName;
    private String description;
    private String classification;
    private String sensitivityLevel;
    private Boolean incrementalCandidate;
    private String definitionStatus;
    private LocalDateTime lastScanTime;
}
