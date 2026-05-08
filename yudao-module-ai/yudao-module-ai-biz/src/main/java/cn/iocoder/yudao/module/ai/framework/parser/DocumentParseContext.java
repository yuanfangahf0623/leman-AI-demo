package cn.iocoder.yudao.module.ai.framework.parser;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文档解析上下文。
 *
 * <p>上下文只传递解析所需的业务标识和文件基础信息，日志中不得输出文件路径或文件内容。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentParseContext {

    private Long documentId;

    private Long tenantId;

    private Long knowledgeBaseId;

    private String filename;

    private String contentType;

    private String fileType;

}
