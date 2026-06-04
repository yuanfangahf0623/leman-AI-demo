package cn.iocoder.yudao.module.ai.framework.ocr;

import cn.iocoder.yudao.module.ai.framework.config.AiProperties;
import cn.iocoder.yudao.module.ai.framework.config.AiProperties.ModelProperties;
import cn.iocoder.yudao.module.ai.framework.config.AiProperties.OcrProperties;
import cn.iocoder.yudao.module.ai.framework.parser.DocumentParseContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI compatible vision OCR fallback.
 */
@Component
public class OpenAiCompatibleVisionOcrService implements ImageRecognitionService {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleVisionOcrService.class);
    private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";
    private static final String PROVIDER = "openai-compatible-vision";
    private static final int HTTP_SUCCESS_MIN = 200;
    private static final int HTTP_SUCCESS_MAX = 299;
    private static final int DEFAULT_CONNECT_TIMEOUT_SECONDS = 10;

    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient;

    public OpenAiCompatibleVisionOcrService(AiProperties aiProperties) {
        this.aiProperties = aiProperties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(DEFAULT_CONNECT_TIMEOUT_SECONDS))
                .build();
    }

    @Override
    public boolean isEnabled() {
        OcrProperties ocr = ocrProperties();
        ModelProperties model = modelProperties();
        return Boolean.TRUE.equals(ocr.getEnabled())
                && Boolean.TRUE.equals(ocr.getVisionFallbackEnabled())
                && hasText(model.getBaseUrl())
                && hasText(model.getApiKey())
                && hasText(resolveVisionModel(ocr));
    }

    public OcrResult recognizePdf(PDDocument document, DocumentParseContext context) throws OcrException {
        if (!isEnabled()) {
            throw new OcrException("Vision OCR fallback is not enabled");
        }
        OcrProperties ocr = ocrProperties();
        int pageCount = Math.min(document.getNumberOfPages(), resolveMaxPages(ocr));
        int dpi = resolveDpi(ocr);
        try {
            PDFRenderer renderer = new PDFRenderer(document);
            List<BufferedImage> images = new ArrayList<>(pageCount);
            for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
                images.add(renderer.renderImageWithDPI(pageIndex, dpi));
            }
            OcrResult result = recognizeBufferedImages(images, context, document.getNumberOfPages(),
                    images.size() < document.getNumberOfPages(), "pdf");
            result.getMetadata().put("ocrVisionTotalPageCount", document.getNumberOfPages());
            result.getMetadata().put("ocrVisionMaxPagesReached", images.size() < document.getNumberOfPages());
            return result;
        } catch (IOException ex) {
            throw new OcrException("Vision OCR fallback image rendering failed", ex);
        }
    }

    @Override
    public OcrResult recognizeImages(List<BufferedImage> images, DocumentParseContext context) throws OcrException {
        if (!isEnabled()) {
            throw new OcrException("Vision OCR fallback is not enabled");
        }
        return recognizeBufferedImages(images, context, images == null ? 0 : images.size(), false, "image");
    }

    private OcrResult recognizeBufferedImages(List<BufferedImage> images, DocumentParseContext context,
                                              int totalImageCount, boolean maxPagesReached, String sourceType)
            throws OcrException {
        if (images == null || images.isEmpty()) {
            throw new OcrException("Vision OCR fallback has no valid images");
        }
        Instant start = Instant.now();
        OcrProperties ocr = ocrProperties();
        String model = resolveVisionModel(ocr);
        List<String> imageDataUrls = buildImageDataUrls(images, ocr);
        if (imageDataUrls.isEmpty()) {
            throw new OcrException("Vision OCR fallback has no valid images");
        }

        URI uri = buildChatCompletionsUri();
        String requestBody = buildRequestBody(model, imageDataUrls);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(resolveTimeoutSeconds(ocr)))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + modelProperties().getApiKey().trim())
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();
        long startNanos = System.nanoTime();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            long elapsedMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
            if (response.statusCode() < HTTP_SUCCESS_MIN || response.statusCode() > HTTP_SUCCESS_MAX) {
                log.warn("Vision OCR fallback failed, endpoint={}, model={}, pages={}, status={}, elapsedMs={}, apiKey={}",
                        sanitizeEndpoint(uri), model, imageDataUrls.size(), response.statusCode(), elapsedMs,
                        maskApiKey(modelProperties().getApiKey()));
                throw new OcrException("Vision OCR fallback request failed");
            }
            String content = parseResponse(response.body());
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("ocr", true);
            metadata.put("ocrProvider", PROVIDER);
            metadata.put("ocrSourceType", sourceType);
            metadata.put("ocrVisionModel", model);
            metadata.put("ocrVisionPageCount", imageDataUrls.size());
            metadata.put("ocrVisionTotalPageCount", totalImageCount);
            metadata.put("ocrVisionMaxPagesReached", maxPagesReached);
            metadata.put("ocrVisionCharCount", content == null ? 0 : content.length());
            metadata.put("ocrVisionDurationMs", Duration.between(start, Instant.now()).toMillis());
            log.info("Vision OCR fallback success, documentId={}, tenantId={}, knowledgeBaseId={}, model={}, sourceType={}, images={}, charCount={}, elapsedMs={}",
                    context.getDocumentId(), context.getTenantId(), context.getKnowledgeBaseId(), model, sourceType,
                    imageDataUrls.size(), metadata.get("ocrVisionCharCount"), elapsedMs);
            return new OcrResult(content == null ? "" : content, metadata);
        } catch (HttpTimeoutException ex) {
            logVisionException(uri, model, imageDataUrls.size(), startNanos, "timeout", ex);
            throw new OcrException("Vision OCR fallback request timeout", ex);
        } catch (IOException ex) {
            logVisionException(uri, model, imageDataUrls.size(), startNanos, "io", ex);
            throw new OcrException("Vision OCR fallback request failed", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logVisionException(uri, model, imageDataUrls.size(), startNanos, "interrupted", ex);
            throw new OcrException("Vision OCR fallback request interrupted", ex);
        }
    }

    private List<String> buildImageDataUrls(List<BufferedImage> images, OcrProperties ocr) throws OcrException {
        int maxImageBytes = resolveMaxImageBytes(ocr);
        try {
            List<String> imageDataUrls = new ArrayList<>(images.size());
            for (int imageIndex = 0; imageIndex < images.size(); imageIndex++) {
                BufferedImage image = images.get(imageIndex);
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                ImageIO.write(image, "png", outputStream);
                byte[] bytes = outputStream.toByteArray();
                if (bytes.length > maxImageBytes) {
                    log.warn("Vision OCR image skipped because image is too large, imageNo={}, sizeBytes={}, maxBytes={}",
                            imageIndex + 1, bytes.length, maxImageBytes);
                    continue;
                }
                imageDataUrls.add("data:image/png;base64," + Base64.getEncoder().encodeToString(bytes));
            }
            return imageDataUrls;
        } catch (IOException ex) {
            throw new OcrException("Vision OCR fallback image rendering failed", ex);
        }
    }

    private String buildRequestBody(String model, List<String> imageDataUrls) throws OcrException {
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", model);
        requestBody.put("temperature", 0);
        requestBody.put("messages", buildMessages(imageDataUrls));
        try {
            return objectMapper.writeValueAsString(requestBody);
        } catch (JsonProcessingException ex) {
            throw new OcrException("Vision OCR fallback request build failed", ex);
        }
    }

    private List<Map<String, Object>> buildMessages(List<String> imageDataUrls) {
        List<Map<String, Object>> messages = new ArrayList<>(2);
        Map<String, Object> systemMessage = new LinkedHashMap<>();
        systemMessage.put("role", "system");
        systemMessage.put("content", "You are an OCR assistant. Extract all visible text from document images. Keep page order. Do not summarize or invent content.");
        messages.add(systemMessage);

        List<Map<String, Object>> content = new ArrayList<>(imageDataUrls.size() + 1);
        content.add(Map.of("type", "text", "text", "请逐页识别图片中的全部可见文字。只输出识别出的原文，保持段落、表格和页码顺序，不要总结，不要补充不存在的内容。"));
        for (String imageDataUrl : imageDataUrls) {
            content.add(Map.of("type", "image_url", "image_url", Map.of("url", imageDataUrl)));
        }
        Map<String, Object> userMessage = new LinkedHashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", content);
        messages.add(userMessage);
        return messages;
    }

    private String parseResponse(String responseBody) throws OcrException {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                throw new OcrException("Vision OCR fallback response missing choices");
            }
            JsonNode contentNode = choices.get(0).path("message").path("content");
            if (!contentNode.isTextual()) {
                throw new OcrException("Vision OCR fallback response content invalid");
            }
            return contentNode.asText();
        } catch (JsonProcessingException ex) {
            throw new OcrException("Vision OCR fallback response parse failed", ex);
        }
    }

    private URI buildChatCompletionsUri() throws OcrException {
        String baseUrl = modelProperties().getBaseUrl();
        if (!hasText(baseUrl)) {
            throw new OcrException("Vision OCR fallback base-url is missing");
        }
        String normalizedBaseUrl = trimTrailingSlash(baseUrl.trim());
        String url = normalizedBaseUrl.endsWith(CHAT_COMPLETIONS_PATH)
                ? normalizedBaseUrl : normalizedBaseUrl + CHAT_COMPLETIONS_PATH;
        try {
            URI uri = URI.create(url);
            if (uri.getScheme() == null || uri.getHost() == null) {
                throw new IllegalArgumentException("base-url must include scheme and host");
            }
            return uri;
        } catch (IllegalArgumentException ex) {
            throw new OcrException("Vision OCR fallback base-url invalid", ex);
        }
    }

    private ModelProperties modelProperties() {
        return aiProperties.getModel() == null ? new ModelProperties() : aiProperties.getModel();
    }

    private OcrProperties ocrProperties() {
        return aiProperties.getDocument().getOcr();
    }

    private String resolveVisionModel(OcrProperties ocr) {
        if (hasText(ocr.getVisionModel())) {
            return ocr.getVisionModel().trim();
        }
        return "gpt-4o";
    }

    private int resolveDpi(OcrProperties ocr) {
        Integer dpi = ocr.getDpi();
        if (dpi == null || dpi < 72) {
            return 200;
        }
        return Math.min(dpi, 200);
    }

    private int resolveMaxPages(OcrProperties ocr) {
        Integer maxPages = ocr.getVisionFallbackMaxPages();
        if (maxPages == null || maxPages <= 0) {
            return 5;
        }
        return Math.min(maxPages, Math.max(1, documentOcrMaxPages(ocr)));
    }

    private int documentOcrMaxPages(OcrProperties ocr) {
        Integer maxPages = ocr.getMaxPages();
        if (maxPages == null || maxPages <= 0) {
            return 20;
        }
        return maxPages;
    }

    private int resolveMaxImageBytes(OcrProperties ocr) {
        Integer maxImageBytes = ocr.getVisionFallbackMaxImageBytes();
        if (maxImageBytes == null || maxImageBytes <= 0) {
            return 6291456;
        }
        return maxImageBytes;
    }

    private long resolveTimeoutSeconds(OcrProperties ocr) {
        Integer timeoutSeconds = ocr.getVisionFallbackTimeoutSeconds();
        if (timeoutSeconds == null || timeoutSeconds <= 0) {
            return 90L;
        }
        return timeoutSeconds.longValue();
    }

    private void logVisionException(URI uri, String model, int pageCount, long startNanos, String reason, Exception ex) {
        log.warn("Vision OCR fallback exception, endpoint={}, model={}, pages={}, reason={}, elapsedMs={}, apiKey={}, errorType={}",
                sanitizeEndpoint(uri), model, pageCount, reason,
                Duration.ofNanos(System.nanoTime() - startNanos).toMillis(),
                maskApiKey(modelProperties().getApiKey()), ex.getClass().getSimpleName());
    }

    private String sanitizeEndpoint(URI uri) {
        StringBuilder endpoint = new StringBuilder();
        endpoint.append(uri.getScheme()).append("://").append(uri.getHost());
        if (uri.getPort() > 0) {
            endpoint.append(":").append(uri.getPort());
        }
        endpoint.append(uri.getPath());
        return endpoint.toString();
    }

    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return "";
        }
        String trimmed = apiKey.trim();
        if (trimmed.length() <= 8) {
            return "****";
        }
        return trimmed.substring(0, 4) + "****" + trimmed.substring(trimmed.length() - 4);
    }

    private String trimTrailingSlash(String value) {
        String result = value;
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

}
