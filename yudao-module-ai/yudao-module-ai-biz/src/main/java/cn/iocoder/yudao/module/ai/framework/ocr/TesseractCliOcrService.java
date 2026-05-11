package cn.iocoder.yudao.module.ai.framework.ocr;

import cn.iocoder.yudao.module.ai.framework.config.AiProperties;
import cn.iocoder.yudao.module.ai.framework.config.AiProperties.OcrProperties;
import cn.iocoder.yudao.module.ai.framework.parser.DocumentParseContext;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * 基于本地 Tesseract CLI 的 OCR 实现。
 *
 * <p>该实现不会读取任何 API Key。启用前需要在部署环境安装 Tesseract，并通过配置指定可执行文件。</p>
 */
@Component
public class TesseractCliOcrService implements OcrService {

    private static final Logger log = LoggerFactory.getLogger(TesseractCliOcrService.class);
    private static final String PROVIDER = "tesseract-cli";

    private final AiProperties aiProperties;

    public TesseractCliOcrService(AiProperties aiProperties) {
        this.aiProperties = aiProperties;
    }

    @Override
    public boolean isEnabled() {
        OcrProperties properties = getProperties();
        return Boolean.TRUE.equals(properties.getEnabled()) && PROVIDER.equalsIgnoreCase(properties.getProvider());
    }

    @Override
    public OcrResult recognizePdf(PDDocument document, DocumentParseContext context) throws OcrException {
        if (!isEnabled()) {
            throw new OcrException("OCR 未启用");
        }
        Instant start = Instant.now();
        OcrProperties properties = getProperties();
        int pageCount = document.getNumberOfPages();
        int ocrPageCount = Math.min(pageCount, resolveMaxPages(properties));
        int dpi = resolveDpi(properties);
        Path tempDirectory = null;
        try {
            tempDirectory = Files.createTempDirectory("ai-ocr-");
            PDFRenderer renderer = new PDFRenderer(document);
            StringBuilder content = new StringBuilder();
            for (int pageIndex = 0; pageIndex < ocrPageCount; pageIndex++) {
                BufferedImage image = renderer.renderImageWithDPI(pageIndex, dpi);
                Path imagePath = tempDirectory.resolve("page-" + (pageIndex + 1) + ".png");
                ImageIO.write(image, "png", imagePath.toFile());
                content.append(runTesseract(imagePath, properties));
                if (pageIndex < ocrPageCount - 1) {
                    content.append('\n');
                }
            }

            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("ocr", true);
            metadata.put("ocrProvider", PROVIDER);
            metadata.put("ocrLanguage", properties.getLanguage());
            metadata.put("ocrDpi", dpi);
            metadata.put("ocrPageCount", ocrPageCount);
            metadata.put("ocrTotalPageCount", pageCount);
            metadata.put("ocrMaxPagesReached", ocrPageCount < pageCount);
            metadata.put("ocrCharCount", content.length());
            metadata.put("ocrDurationMs", Duration.between(start, Instant.now()).toMillis());
            log.info("PDF OCR 识别完成，documentId={}, tenantId={}, knowledgeBaseId={}, pages={}, durationMs={}",
                    context.getDocumentId(), context.getTenantId(), context.getKnowledgeBaseId(), ocrPageCount,
                    metadata.get("ocrDurationMs"));
            return new OcrResult(content.toString(), metadata);
        } catch (IOException ex) {
            log.warn("PDF OCR 引擎调用失败，documentId={}, tenantId={}, knowledgeBaseId={}, errorType={}",
                    context.getDocumentId(), context.getTenantId(), context.getKnowledgeBaseId(),
                    ex.getClass().getSimpleName());
            throw new OcrException("OCR 引擎不可用，请检查 Tesseract 配置", ex);
        } finally {
            deleteQuietly(tempDirectory);
        }
    }

    private String runTesseract(Path imagePath, OcrProperties properties) throws OcrException, IOException {
        List<String> command = new ArrayList<>();
        command.add(resolveExecutable(properties));
        command.add(imagePath.toString());
        command.add("stdout");
        if (properties.getTessdataDirectory() != null && !properties.getTessdataDirectory().isBlank()) {
            command.add("--tessdata-dir");
            command.add(properties.getTessdataDirectory());
        }
        command.add("-l");
        command.add(resolveLanguage(properties));
        command.add("--psm");
        command.add("6");
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        Process process = processBuilder.start();
        CompletableFuture<String> outputFuture = CompletableFuture.supplyAsync(() -> readOutput(process));
        CompletableFuture<String> errorFuture = CompletableFuture.supplyAsync(() -> readError(process));
        boolean finished;
        try {
            finished = process.waitFor(resolveTimeoutSeconds(properties), TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new OcrException("OCR 识别被中断", ex);
        }
        if (!finished) {
            process.destroyForcibly();
            outputFuture.cancel(true);
            errorFuture.cancel(true);
            throw new OcrException("OCR 识别超时");
        }
        String output = outputFuture.join();
        errorFuture.join();
        if (process.exitValue() != 0) {
            throw new OcrException("OCR 识别失败，退出码：" + process.exitValue());
        }
        return output;
    }

    private String readOutput(Process process) {
        try {
            return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            return "";
        }
    }

    private String readError(Process process) {
        try {
            return new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            return "";
        }
    }

    private OcrProperties getProperties() {
        return aiProperties.getDocument().getOcr();
    }

    private String resolveExecutable(OcrProperties properties) {
        return properties.getTesseractExecutable() == null || properties.getTesseractExecutable().isBlank()
                ? "tesseract" : properties.getTesseractExecutable();
    }

    private String resolveLanguage(OcrProperties properties) {
        return properties.getLanguage() == null || properties.getLanguage().isBlank()
                ? "chi_sim+eng" : properties.getLanguage();
    }

    private int resolveDpi(OcrProperties properties) {
        Integer dpi = properties.getDpi();
        if (dpi == null || dpi < 72) {
            return 200;
        }
        return Math.min(dpi, 300);
    }

    private int resolveMaxPages(OcrProperties properties) {
        Integer maxPages = properties.getMaxPages();
        if (maxPages == null || maxPages <= 0) {
            return 20;
        }
        return maxPages;
    }

    private long resolveTimeoutSeconds(OcrProperties properties) {
        Integer timeoutSeconds = properties.getTimeoutSeconds();
        if (timeoutSeconds == null || timeoutSeconds <= 0) {
            return 60L;
        }
        return timeoutSeconds.longValue();
    }

    private void deleteQuietly(Path directory) {
        if (directory == null || !Files.exists(directory)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(directory)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // 临时 OCR 文件删除失败不影响主流程，后续由系统临时目录清理。
                }
            });
        } catch (IOException ignored) {
            // 临时 OCR 目录删除失败不影响主流程。
        }
    }

}
