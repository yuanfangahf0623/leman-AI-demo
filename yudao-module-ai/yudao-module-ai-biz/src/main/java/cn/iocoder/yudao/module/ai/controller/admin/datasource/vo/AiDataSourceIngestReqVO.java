package cn.iocoder.yudao.module.ai.controller.admin.datasource.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * API data source ingest request.
 */
@Data
public class AiDataSourceIngestReqVO {

    @NotNull(message = "数据源编号不能为空")
    private Long dataSourceId;

    private Long directoryId;

    @Size(max = 512, message = "外部编号不能超过 512 个字符")
    private String externalId;

    @Size(max = 1024, message = "来源地址不能超过 1024 个字符")
    private String sourceUri;

    @NotBlank(message = "标题不能为空")
    @Size(max = 255, message = "标题不能超过 255 个字符")
    private String title;

    @NotBlank(message = "内容不能为空")
    @Size(max = 2_000_000, message = "内容不能超过 2000000 个字符")
    private String content;

    @Size(max = 64, message = "文档版本不能超过 64 个字符")
    private String documentVersion;

    private List<@Size(max = 64, message = "标签不能超过 64 个字符") String> tags;

    private Map<String, Object> metadata;

}
