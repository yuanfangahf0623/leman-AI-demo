package cn.iocoder.yudao.module.ai.controller.admin.document.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * AI 文档更新请求。
 */
@Data
public class AiDocumentUpdateReqVO {

    @NotNull(message = "文档编号不能为空")
    private Long id;

    private Long directoryId;

    @NotBlank(message = "文档标题不能为空")
    @Size(max = 255, message = "文档标题不能超过 255 个字符")
    private String title;

    @Size(max = 64, message = "文档版本不能超过 64 个字符")
    private String documentVersion;

}
