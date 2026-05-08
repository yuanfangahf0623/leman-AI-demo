package cn.iocoder.yudao.module.ai.framework.parser;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

/**
 * TXT 文档解析器。
 */
@Component
public class TxtDocumentParser implements DocumentParser {

    private static final Set<String> EXTENSIONS = Set.of("txt");

    @Override
    public boolean supports(String filename, String contentType) {
        return DocumentParseUtils.hasExtension(filename, EXTENSIONS)
                || DocumentParseUtils.contentTypeContains(contentType, "text/plain");
    }

    @Override
    public ParsedDocument parse(InputStream inputStream, DocumentParseContext context) throws DocumentParseException {
        try {
            String content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            content = removeUtf8Bom(content);
            Map<String, Object> metadata = DocumentParseUtils.baseMetadata(context, getClass().getSimpleName(), "txt");
            metadata.put("charCount", content.length());
            metadata.put("lineCount", DocumentParseUtils.countLines(content));
            return new ParsedDocument(DocumentParseUtils.titleFromFilename(context.getFilename(), "TXT Document"),
                    content, metadata);
        } catch (IOException ex) {
            throw new DocumentParseException("TXT 文档解析失败", ex);
        }
    }

    private String removeUtf8Bom(String content) {
        if (!content.isEmpty() && content.charAt(0) == '\uFEFF') {
            return content.substring(1);
        }
        return content;
    }

}
