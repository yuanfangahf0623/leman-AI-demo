package cn.iocoder.yudao.module.dataplatform.controller.admin.sync.vo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import lombok.Data;

import java.util.List;

@Data
public class SyncJobSaveReqVO {

    private Long id;
    @NotBlank @Size(max = 100)
    private String name;
    @NotBlank
    @Pattern(regexp = "^[a-z][a-z0-9_]{1,63}$", message = "任务编码只能包含小写字母、数字和下划线")
    private String code;
    @NotNull
    private Long sourceDataSourceId;
    @NotBlank @Size(max = 8000)
    private String sourceSql;
    @NotNull
    private Long targetDataSourceId;
    @NotBlank
    @Pattern(regexp = "^[A-Za-z0-9_]{1,64}$", message = "目标库名包含非法字符")
    private String targetDatabase;
    @NotBlank
    @Pattern(regexp = "^[A-Za-z0-9_]{1,128}$", message = "目标表名包含非法字符")
    private String targetTable;
    @Size(max = 8000)
    private String sinkSql;
    @Valid
    @Size(max = 500)
    private List<SyncFieldMappingVO> fieldMappings;
    @NotBlank
    @Pattern(regexp = "FULL|INCREMENTAL", message = "同步模式只能为 FULL 或 INCREMENTAL")
    private String syncMode;
    @Size(max = 128)
    private String watermarkColumn;
    @Size(max = 500)
    private String watermarkValue;
    @Min(1) @Max(16)
    private Integer parallelism;
    private Integer status;
    @Size(max = 500)
    private String remark;
}
