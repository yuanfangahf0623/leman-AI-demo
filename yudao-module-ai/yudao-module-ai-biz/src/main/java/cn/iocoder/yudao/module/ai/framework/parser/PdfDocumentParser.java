package cn.iocoder.yudao.module.ai.framework.parser;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Set;

/**
 * PDF 文档解析器。
 */
@Component
public class PdfDocumentParser implements DocumentParser {

    private static final Set<String> EXTENSIONS = Set.of("pdf");

    @Override
    public boolean supports(String filename, String contentType) {
        return DocumentParseUtils.hasExtension(filename, EXTENSIONS)
                || DocumentParseUtils.contentTypeContains(contentType, "application/pdf");
    }

    @Override
    public ParsedDocument parse(InputStream inputStream, DocumentParseContext context) throws DocumentParseException {
        try (PDDocument document = PDDocument.load(inputStream)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String content = stripper.getText(document);
            Map<String, Object> metadata = DocumentParseUtils.baseMetadata(context, getClass().getSimpleName(), "pdf");
            metadata.put("pageCount", document.getNumberOfPages());
            metadata.put("charCount", content.length());
            return new ParsedDocument(resolveTitle(document, context), content, metadata);
        } catch (IOException ex) {
            throw new DocumentParseException("PDF 文档解析失败", ex);
        }
    }

    private String resolveTitle(PDDocument document, DocumentParseContext context) {
        PDDocumentInformation information = document.getDocumentInformation();
        if (information != null && information.getTitle() != null && !information.getTitle().isBlank()) {
            return information.getTitle().trim();
        }
        return DocumentParseUtils.titleFromFilename(context.getFilename(), "PDF Document");
    }

}
