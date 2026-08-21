package cn.iocoder.yudao.module.dataplatform.controller.admin.sync.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class SyncJobRespVO {

    private Long id;
    private String name;
    private String code;
    private Long sourceDataSourceId;
    private String sourceSql;
    private Long targetDataSourceId;
    private String targetDatabase;
    private String targetTable;
    private String sinkSql;
    private List<SyncFieldMappingVO> fieldMappings;
    private String syncMode;
    private String watermarkColumn;
    private String watermarkValue;
    private Integer parallelism;
    private Integer status;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
