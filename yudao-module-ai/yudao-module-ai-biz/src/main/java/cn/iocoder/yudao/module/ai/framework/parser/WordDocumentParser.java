package cn.iocoder.yudao.module.ai.framework.parser;

import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Set;

/**
 * Word 文档解析器，支持 doc、docx 和 WPS 兼容格式。
 */
@Component
public class WordDocumentParser implements DocumentParser {

    private static final Set<String> EXTENSIONS = Set.of("doc", "docx", "wps");

    @Override
    public boolean supports(String filename, String contentType) {
        return DocumentParseUtils.hasExtension(filename, EXTENSIONS)
                || DocumentParseUtils.contentTypeContains(contentType, "msword")
                || DocumentParseUtils.contentTypeContains(contentType, "wordprocessingml")
                || DocumentParseUtils.contentTypeContains(contentType, "wps");
    }

    @Override
    public ParsedDocument parse(InputStream inputStream, DocumentParseContext context) throws DocumentParseException {
        try {
            byte[] bytes = inputStream.readAllBytes();
            String extension = DocumentParseUtils.extension(context.getFilename());
            String content = switch (extension) {
                case "doc" -> parseDoc(bytes);
                case "docx" -> parseDocx(bytes);
                case "wps" -> parseWps(bytes);
                default -> parseByHeader(bytes);
            };
            Map<String, Object> metadata = DocumentParseUtils.baseMetadata(context, getClass().getSimpleName(), extension);
            metadata.put("charCount", content.length());
            metadata.put("lineCount", DocumentParseUtils.countLines(content));
            return new ParsedDocument(DocumentParseUtils.titleFromFilename(context.getFilename(), "Word Document"),
                    content, metadata);
        } catch (IOException | RuntimeException ex) {
            throw new DocumentParseException("Word 文档解析失败", ex);
        }
    }

    private String parseByHeader(byte[] bytes) throws IOException {
        if (DocumentParseUtils.isZip(bytes)) {
            return parseDocx(bytes);
        }
        return parseDoc(bytes);
    }

    private String parseWps(byte[] bytes) throws IOException {
        if (DocumentParseUtils.isZip(bytes)) {
            return parseDocx(bytes);
        }
        if (DocumentParseUtils.isOle2(bytes)) {
            return parseDoc(bytes);
        }
        return parseByHeader(bytes);
    }

    private String parseDoc(byte[] bytes) throws IOException {
        try (HWPFDocument document = new HWPFDocument(new ByteArrayInputStream(bytes));
             WordExtractor extractor = new WordExtractor(document)) {
            return extractor.getText();
        }
    }

    private String parseDocx(byte[] bytes) throws IOException {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes));
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        }
    }

}
