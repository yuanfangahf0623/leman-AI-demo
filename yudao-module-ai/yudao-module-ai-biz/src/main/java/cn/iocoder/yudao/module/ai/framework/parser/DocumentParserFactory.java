package cn.iocoder.yudao.module.ai.framework.parser;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;

/**
 * 文档解析器选择器。
 */
@Component
@RequiredArgsConstructor
public class DocumentParserFactory {

    private final List<DocumentParser> parsers;

    /**
     * 按后缀和 MIME 类型选择解析器并执行解析。
     */
    public ParsedDocument parse(InputStream inputStream, DocumentParseContext context) throws DocumentParseException {
        DocumentParser parser = parsers.stream()
                .filter(item -> item.supports(context.getFilename(), context.getContentType()))
                .findFirst()
                .orElseThrow(() -> new DocumentParseException("不支持的文档类型"));
        return parser.parse(inputStream, context);
    }

}
