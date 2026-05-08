package cn.iocoder.yudao.module.ai.framework.parser;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown 文档解析器。
 */
@Component
public class MarkdownDocumentParser implements DocumentParser {

    private static final Set<String> EXTENSIONS = Set.of("md");
    private static final Pattern FIRST_HEADING = Pattern.compile("(?m)^#{1,6}\\s+(.+?)\\s*$");

    @Override
    public boolean supports(String filename, String contentType) {
        return DocumentParseUtils.hasExtension(filename, EXTENSIONS)
                || DocumentParseUtils.contentTypeContains(contentType, "markdown");
    }

    @Override
    public ParsedDocument parse(InputStream inputStream, DocumentParseContext context) throws DocumentParseException {
        try {
            String markdown = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            markdown = removeUtf8Bom(markdown);
            String content = toPlainText(markdown);
            Map<String, Object> metadata = DocumentParseUtils.baseMetadata(context, getClass().getSimpleName(), "md");
            metadata.put("charCount", content.length());
            metadata.put("lineCount", DocumentParseUtils.countLines(content));
            return new ParsedDocument(resolveTitle(markdown, context), content, metadata);
        } catch (IOException ex) {
            throw new DocumentParseException("Markdown 文档解析失败", ex);
        }
    }

    private String resolveTitle(String markdown, DocumentParseContext context) {
        Matcher matcher = FIRST_HEADING.matcher(markdown);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return DocumentParseUtils.titleFromFilename(context.getFilename(), "Markdown Document");
    }

    private String toPlainText(String markdown) {
        return markdown
                .replaceAll("(?m)^#{1,6}\\s*", "")
                .replaceAll("!\\[([^]]*)]\\([^)]+\\)", "$1")
                .replaceAll("\\[([^]]+)]\\([^)]+\\)", "$1")
                .replaceAll("(?m)^\\s*>\\s?", "")
                .replaceAll("(?m)^\\s*[-*+]\\s+", "")
                .replaceAll("(?m)^\\s*\\d+\\.\\s+", "")
                .replace("**", "")
                .replace("__", "")
                .replace("`", "");
    }

    private String removeUtf8Bom(String content) {
        if (!content.isEmpty() && content.charAt(0) == '\uFEFF') {
            return content.substring(1);
        }
        return content;
    }

}
