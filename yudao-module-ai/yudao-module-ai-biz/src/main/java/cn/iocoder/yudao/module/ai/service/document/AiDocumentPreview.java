package cn.iocoder.yudao.module.ai.service.document;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.InputStream;

/**
 * AI 文档预览内容。
 */
@Data
@AllArgsConstructor
public class AiDocumentPreview {

    /**
     * 原始展示文件名。
     */
    private String fileName;

    /**
     * 浏览器预览使用的 Content-Type。
     */
    private String contentType;

    /**
     * 文件输入流，由 Web 层写出响应。
     */
    private InputStream inputStream;

}
