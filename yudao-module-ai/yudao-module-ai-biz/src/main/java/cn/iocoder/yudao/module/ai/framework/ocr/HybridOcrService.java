package cn.iocoder.yudao.module.ai.framework.ocr;

import cn.iocoder.yudao.module.ai.framework.config.AiProperties;
import cn.iocoder.yudao.module.ai.framework.config.AiProperties.OcrProperties;
import cn.iocoder.yudao.module.ai.framework.parser.DocumentParseContext;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OCR orchestrator: Tesseract first, vision model fallback when quality is poor.
 */
@Primary
@Component
public class HybridOcrService implements OcrService, ImageRecognitionService {

    private static final Logger log = LoggerFactory.getLogger(HybridOcrService.class);

    private final AiProperties aiProperties;
    private final TesseractCliOcrService tesseractCliOcrService;
    private final OpenAiCompatibleVisionOcrService visionOcrService;

    public HybridOcrService(AiProperties aiProperties, TesseractCliOcrService tesseractCliOcrService,
                            OpenAiCompatibleVisionOcrService visionOcrService) {
        this.aiProperties = aiProperties;
        this.tesseractCliOcrService = tesseractCliOcrService;
        this.visionOcrService = visionOcrService;
    }

    @Override
    public boolean isEnabled() {
        return tesseractCliOcrService.isEnabled() || visionOcrService.isEnabled();
    }

    @Override
    public OcrResult recognizePdf(PDDocument document, DocumentParseContext context) throws OcrException {
        return recognizeWithFallback(context,
                () -> tesseractCliOcrService.recognizePdf(document, context),
                () -> visionOcrService.recognizePdf(document, context),
                "pdf");
    }

    @Override
    public OcrResult recognizeImages(List<BufferedImage> images, DocumentParseContext context) throws OcrException {
        return recognizeWithFallback(context,
                () -> tesseractCliOcrService.recognizeImages(images, context),
                () -> visionOcrService.recognizeImages(images, context),
                "image");
    }

    private OcrResult recognizeWithFallback(DocumentParseContext context, OcrCallable primaryCall,
                                            OcrCallable fallbackCall, String sourceType) throws OcrException {
        OcrResult tesseractResult = null;
        OcrException tesseractException = null;

        if (tesseractCliOcrService.isEnabled()) {
            try {
                tesseractResult = primaryCall.call();
            } catch (OcrException ex) {
                tesseractException = ex;
                log.warn("Tesseract OCR failed before fallback, documentId={}, tenantId={}, knowledgeBaseId={}, sourceType={}, errorType={}",
                        context.getDocumentId(), context.getTenantId(), context.getKnowledgeBaseId(), sourceType,
                        ex.getClass().getSimpleName());
            }
        }

        OcrQuality quality = evaluateQuality(tesseractResult == null ? null : tesseractResult.getContent());
        if (shouldUseVisionFallback(tesseractResult, quality)) {
            OcrResult fallbackResult = tryVisionFallback(context, tesseractResult, quality, fallbackCall, sourceType);
            if (fallbackResult != null) {
                return fallbackResult;
            }
        }

        if (tesseractResult != null) {
            tesseractResult.getMetadata().put("ocrQualityReason", quality.reason());
            tesseractResult.getMetadata().put("ocrGarbledRatio", quality.garbledRatio());
            tesseractResult.getMetadata().put("ocrVisionFallbackApplied", false);
            return tesseractResult;
        }
        if (tesseractException != null) {
            throw tesseractException;
        }
        if (visionOcrService.isEnabled()) {
            return fallbackCall.call();
        }
        throw new OcrException("OCR is not enabled");
    }

    private OcrResult tryVisionFallback(DocumentParseContext context, OcrResult tesseractResult, OcrQuality quality,
                                        OcrCallable fallbackCall, String sourceType) {
        if (!visionOcrService.isEnabled()) {
            if (tesseractResult != null) {
                tesseractResult.getMetadata().put("ocrVisionFallbackApplied", false);
                tesseractResult.getMetadata().put("ocrVisionFallbackSkippedReason", "disabled-or-config-missing");
            }
            return null;
        }
        try {
            OcrResult visionResult = fallbackCall.call();
            if (visionResult.getContent() == null || visionResult.getContent().trim().isEmpty()) {
                if (tesseractResult != null) {
                    tesseractResult.getMetadata().put("ocrVisionFallbackApplied", false);
                    tesseractResult.getMetadata().put("ocrVisionFallbackSkippedReason", "empty-result");
                }
                return null;
            }
            Map<String, Object> metadata = new LinkedHashMap<>(visionResult.getMetadata());
            metadata.put("ocrPrimaryProvider", tesseractResult == null
                    ? "none" : tesseractResult.getMetadata().getOrDefault("ocrProvider", "tesseract-cli"));
            metadata.put("ocrPrimaryCharCount", tesseractResult == null || tesseractResult.getContent() == null
                    ? 0 : tesseractResult.getContent().length());
            metadata.put("ocrQualityReason", quality.reason());
            metadata.put("ocrGarbledRatio", quality.garbledRatio());
            metadata.put("ocrVisionFallbackApplied", true);
            log.info("Vision OCR fallback applied, documentId={}, tenantId={}, knowledgeBaseId={}, sourceType={}, reason={}, garbledRatio={}",
                    context.getDocumentId(), context.getTenantId(), context.getKnowledgeBaseId(), sourceType,
                    quality.reason(), quality.garbledRatio());
            return new OcrResult(visionResult.getContent(), metadata);
        } catch (OcrException ex) {
            if (tesseractResult != null) {
                tesseractResult.getMetadata().put("ocrVisionFallbackApplied", false);
                tesseractResult.getMetadata().put("ocrVisionFallbackSkippedReason", "fallback-failed");
            }
            log.warn("Vision OCR fallback failed, documentId={}, tenantId={}, knowledgeBaseId={}, sourceType={}, reason={}, errorType={}",
                    context.getDocumentId(), context.getTenantId(), context.getKnowledgeBaseId(), sourceType,
                    quality.reason(), ex.getClass().getSimpleName());
            return null;
        }
    }

    private boolean shouldUseVisionFallback(OcrResult tesseractResult, OcrQuality quality) {
        if (tesseractResult == null) {
            return true;
        }
        return !quality.acceptable();
    }

    private OcrQuality evaluateQuality(String content) {
        String text = content == null ? "" : content.trim();
        int minTextLength = resolveFallbackMinTextLength();
        if (text.length() < minTextLength) {
            return new OcrQuality(false, "empty-or-too-short", 0D);
        }
        double garbledRatio = calculateGarbledRatio(text);
        if (garbledRatio >= resolveGarbledRatioThreshold()) {
            return new OcrQuality(false, "garbled-text", garbledRatio);
        }
        return new OcrQuality(true, "acceptable", garbledRatio);
    }

    private double calculateGarbledRatio(String text) {
        int visibleCount = 0;
        int garbledCount = 0;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (Character.isWhitespace(ch)) {
                continue;
            }
            visibleCount++;
            if (isGarbled(ch)) {
                garbledCount++;
            }
        }
        if (visibleCount == 0) {
            return 1D;
        }
        return (double) garbledCount / visibleCount;
    }

    private boolean isGarbled(char ch) {
        return ch == '\uFFFD'
                || ch == '\u25A1'
                || ch == '\u25AF'
                || ch == '\u25A0'
                || Character.isISOControl(ch);
    }

    private int resolveFallbackMinTextLength() {
        OcrProperties ocr = aiProperties.getDocument().getOcr();
        Integer value = ocr.getVisionFallbackMinTextLength();
        if (value == null || value <= 0) {
            return 40;
        }
        return value;
    }

    private double resolveGarbledRatioThreshold() {
        OcrProperties ocr = aiProperties.getDocument().getOcr();
        Double value = ocr.getVisionFallbackGarbledRatioThreshold();
        if (value == null || value <= 0D) {
            return 0.25D;
        }
        return Math.min(value, 1D);
    }

    private record OcrQuality(boolean acceptable, String reason, double garbledRatio) {
    }

    @FunctionalInterface
    private interface OcrCallable {

        OcrResult call() throws OcrException;

    }

}
