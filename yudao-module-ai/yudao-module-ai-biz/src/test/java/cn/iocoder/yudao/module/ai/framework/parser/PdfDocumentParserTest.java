package cn.iocoder.yudao.module.ai.framework.parser;

import cn.iocoder.yudao.module.ai.framework.config.AiProperties;
import cn.iocoder.yudao.module.ai.framework.ocr.OcrResult;
import cn.iocoder.yudao.module.ai.framework.ocr.OcrService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PdfDocumentParserTest {

    @Mock
    private OcrService ocrService;

    @Test
    void parseShouldFallbackToOcrWhenPdfTextIsEmpty() throws Exception {
        AiProperties aiProperties = new AiProperties();
        when(ocrService.isEnabled()).thenReturn(true);
        when(ocrService.recognizePdf(any(PDDocument.class), any(DocumentParseContext.class)))
                .thenReturn(new OcrResult("OCR 提取文本", Map.of("ocrPageCount", 1)));
        PdfDocumentParser parser = new PdfDocumentParser(aiProperties, ocrService);

        ParsedDocument parsedDocument = parser.parse(new ByteArrayInputStream(createBlankPdfBytes()), buildContext());

        assertEquals("OCR 提取文本", parsedDocument.getContent());
        assertEquals("ocr", parsedDocument.getMetadata().get("textExtraction"));
        assertEquals(true, parsedDocument.getMetadata().get("ocrApplied"));
        assertEquals(1, parsedDocument.getMetadata().get("ocrPageCount"));
        verify(ocrService, times(2)).isEnabled();
        verify(ocrService).recognizePdf(any(PDDocument.class), any(DocumentParseContext.class));
        verifyNoMoreInteractions(ocrService);
    }

    @Test
    void parseShouldSkipOcrWhenPdfHasSearchableText() throws Exception {
        AiProperties aiProperties = new AiProperties();
        when(ocrService.isEnabled()).thenReturn(true);
        PdfDocumentParser parser = new PdfDocumentParser(aiProperties, ocrService);

        ParsedDocument parsedDocument = parser.parse(new ByteArrayInputStream(createTextPdfBytes()), buildContext());

        assertTrue(parsedDocument.getContent().contains("searchable text"));
        assertEquals("pdfbox", parsedDocument.getMetadata().get("textExtraction"));
        verify(ocrService).isEnabled();
        verifyNoMoreInteractions(ocrService);
    }

    private DocumentParseContext buildContext() {
        return DocumentParseContext.builder()
                .documentId(1L)
                .tenantId(1L)
                .knowledgeBaseId(10L)
                .filename("test.pdf")
                .contentType("application/pdf")
                .fileType("pdf")
                .build();
    }

    private byte[] createBlankPdfBytes() throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(outputStream);
            return outputStream.toByteArray();
        }
    }

    private byte[] createTextPdfBytes() throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.beginText();
                contentStream.setFont(PDType1Font.HELVETICA, 12);
                contentStream.newLineAtOffset(100, 700);
                contentStream.showText("This PDF contains enough searchable text for parser.");
                contentStream.endText();
            }
            document.save(outputStream);
            return outputStream.toByteArray();
        }
    }

}
