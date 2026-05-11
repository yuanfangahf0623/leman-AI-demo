package cn.iocoder.yudao.module.ai.framework.parser;

import cn.iocoder.yudao.module.ai.framework.config.AiProperties;
import cn.iocoder.yudao.module.ai.framework.ocr.OcrException;
import cn.iocoder.yudao.module.ai.framework.ocr.OcrResult;
import cn.iocoder.yudao.module.ai.framework.ocr.OcrService;
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

    private final AiProperties aiProperties;
    private final OcrService ocrService;

    public PdfDocumentParser(AiProperties aiProperties, OcrService ocrService) {
        this.aiProperties = aiProperties;
        this.ocrService = ocrService;
    }

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
            metadata.put("textExtraction", "pdfbox");
            metadata.put("ocrEnabled", ocrService.isEnabled());
            if (shouldUseOcr(content)) {
                content = applyOcr(document, context, metadata, content);
            }
            metadata.put("charCount", content.length());
            return new ParsedDocument(resolveTitle(document, context), content, metadata);
        } catch (IOException ex) {
            throw new DocumentParseException("PDF 文档解析失败", ex);
        }
    }

    private boolean shouldUseOcr(String content) {
        int minTextLength = aiProperties.getDocument().getOcr().getMinTextLengthToSkipOcr() == null
                ? 20 : aiProperties.getDocument().getOcr().getMinTextLengthToSkipOcr();
        return content == null || content.trim().length() < minTextLength;
    }

    private String applyOcr(PDDocument document, DocumentParseContext context, Map<String, Object> metadata,
                            String originalContent)
            throws DocumentParseException {
        if (!ocrService.isEnabled()) {
            metadata.put("ocrApplied", false);
            metadata.put("ocrSkippedReason", "disabled");
            return originalContent == null ? "" : originalContent;
        }
        try {
            OcrResult ocrResult = ocrService.recognizePdf(document, context);
            metadata.putAll(ocrResult.getMetadata());
            metadata.put("ocrApplied", true);
            metadata.put("textExtraction", "ocr");
            return ocrResult.getContent() == null ? "" : ocrResult.getContent();
        } catch (OcrException ex) {
            throw new DocumentParseException("PDF OCR 识别失败", ex);
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
