package cn.iocoder.yudao.module.ai.controller.admin.sync.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * AI sync job create request.
 */
@Data
public class AiSyncJobCreateReqVO {

    @NotNull(message = "知识库编号不能为空")
    private Long knowledgeBaseId;

    @NotNull(message = "数据源编号不能为空")
    private Long dataSourceId;

    @NotBlank(message = "同步任务类型不能为空")
    @Pattern(regexp = "FULL|INCREMENTAL", message = "同步任务类型只支持 FULL、INCREMENTAL")
    private String jobType;

}
