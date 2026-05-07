package cn.iocoder.yudao.module.ai.controller.admin.datasource.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * AI data source request fields.
 */
@Data
public class AiDataSourceBaseVO {

    @NotNull(message = "知识库编号不能为空")
    private Long knowledgeBaseId;

    @NotBlank(message = "数据源名称不能为空")
    @Size(max = 128, message = "数据源名称不能超过 128 个字符")
    private String name;

    @NotBlank(message = "数据源类型不能为空")
    @Pattern(regexp = "FILE|DATABASE|API|WIKI|GIT", message = "数据源类型只支持 FILE、DATABASE、API、WIKI、GIT")
    private String sourceType;

    @NotBlank(message = "同步模式不能为空")
    @Pattern(regexp = "MANUAL|SCHEDULED|INCREMENTAL", message = "同步模式只支持 MANUAL、SCHEDULED、INCREMENTAL")
    private String syncMode;

    private String configJson;

    private Boolean syncEnabled;

    private Integer status;

}
