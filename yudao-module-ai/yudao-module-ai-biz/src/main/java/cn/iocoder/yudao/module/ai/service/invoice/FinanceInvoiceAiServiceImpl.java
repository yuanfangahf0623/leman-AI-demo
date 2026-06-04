package cn.iocoder.yudao.module.ai.service.invoice;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.ai.enums.FinanceInvoiceConstants;
import cn.iocoder.yudao.module.ai.framework.config.AiProperties;
import cn.iocoder.yudao.module.ai.framework.ocr.ImageRecognitionService;
import cn.iocoder.yudao.module.ai.framework.ocr.OcrException;
import cn.iocoder.yudao.module.ai.framework.ocr.OcrResult;
import cn.iocoder.yudao.module.ai.framework.ocr.OcrService;
import cn.iocoder.yudao.module.ai.framework.parser.DocumentParseContext;
import cn.iocoder.yudao.module.ai.framework.tenant.AiTenantContextHolder;
import cn.iocoder.yudao.module.ai.service.chatmodel.AiChatModelRequest;
import cn.iocoder.yudao.module.ai.service.chatmodel.AiChatModelResponse;
import cn.iocoder.yudao.module.ai.service.chatmodel.AiChatModelService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 发票 AI 识别服务。
 *
 * <p>当前流程先复用统一图片识别能力提取文字，再按配置决定是否调用模型抽取结构化字段。
 * 本地开发可继续使用 mock 结果，生产环境可通过 ai.invoice.recognition-provider=model 启用真实模型解析。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FinanceInvoiceAiServiceImpl implements FinanceInvoiceAiService {

    private static final String PROVIDER_MOCK = "mock";
    private static final String PROVIDER_MODEL = "model";
    private static final int DEFAULT_MAX_OCR_CHARS = 12000;
    private static final int DEFAULT_MAX_TOKENS = 1200;
    private static final Pattern AMOUNT_PATTERN = Pattern.compile(
            "(?:金额|付款金额|合计|总额|应付|含税金额|total|amount)[^0-9€$￥]{0,30}"
                    + "(?:EUR|EURO|CNY|RMB|USD|€|￥|\\$)?\\s*([0-9][0-9.,]*)\\s*(?:EUR|EURO|CNY|RMB|USD|€|￥|\\$)?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DUE_DATE_PATTERN = Pattern.compile(
            "(?:付款时间|付款日期|到期日|支付日期|due\\s*date)\\s*[:：]?\\s*"
                    + "(\\d{4}[-/.年]\\d{1,2}[-/.月]\\d{1,2})",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SUPPLIER_PATTERN = Pattern.compile(
            "(?:付款单位|收款单位|供应商|supplier|vendor)\\s*[:：]?\\s*([^\\r\\n|]+)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern IBAN_PATTERN = Pattern.compile("\\b[A-Z]{2}\\d{2}[A-Z0-9\\s]{11,34}\\b");
    private static final Pattern BIC_PATTERN = Pattern.compile("\\b[A-Z]{6}[A-Z0-9]{2}(?:[A-Z0-9]{3})?\\b");

    private final ObjectMapper objectMapper;
    private final OcrService ocrService;
    private final ImageRecognitionService imageRecognitionService;
    private final AiChatModelService aiChatModelService;
    private final AiProperties aiProperties;

    @Override
    public InvoiceAiResult recognizeInvoice(Long invoiceId, String fileUrl, String fileType, byte[] fileContent) {
        OcrExtractionResult ocrExtraction = extractInvoiceText(invoiceId, fileType, fileContent);
        if (isModelRecognitionEnabled()) {
            InvoiceAiResult modelResult = tryRecognizeWithModel(invoiceId, fileUrl, fileType, ocrExtraction);
            if (modelResult != null && modelResult.success()) {
                return modelResult;
            }
            if (!Boolean.TRUE.equals(aiProperties.getInvoice().getRecognitionFallbackToMock())) {
                return modelResult == null ? failedResult("Invoice model recognition failed") : modelResult;
            }
        }
        return mockInvoice(invoiceId, fileUrl, fileType, ocrExtraction, isModelRecognitionEnabled() ? "model-fallback-mock" : PROVIDER_MOCK);
    }

    private InvoiceAiResult tryRecognizeWithModel(Long invoiceId, String fileUrl, String fileType,
                                                  OcrExtractionResult ocrExtraction) {
        if (ocrExtraction.content() == null || ocrExtraction.content().isBlank()) {
            InvoiceAiResult result = failedResult("Invoice OCR text is empty");
            result.setRawJson(toRawJson(result, fileUrl, fileType, "model", ocrExtraction, null, null));
            return result;
        }
        String ocrText = truncateOcrText(normalizeOcrText(ocrExtraction.content()));
        AiChatModelRequest request = AiChatModelRequest.builder()
                .model(blankToNull(aiProperties.getInvoice().getRecognitionModel()))
                .systemPrompt(buildSystemPrompt())
                .userPrompt(buildUserPrompt(fileType, ocrText))
                .temperature(0D)
                .maxTokens(resolveMaxTokens())
                .metadata(Map.of("invoiceId", invoiceId == null ? "" : invoiceId))
                .build();
        try {
            AiChatModelResponse response = aiChatModelService.chat(request);
            InvoiceAiResult result = parseModelResult(response, fileUrl, fileType, ocrExtraction);
            log.info("Invoice model recognition success, invoiceId={}, tenantId={}, model={}, ocrChars={}, promptTokens={}, completionTokens={}",
                    invoiceId, AiTenantContextHolder.getTenantId(), response.getModel(), ocrText.length(),
                    response.getPromptTokens(), response.getCompletionTokens());
            return result;
        } catch (ServiceException | JsonProcessingException ex) {
            log.warn("Invoice model recognition failed, invoiceId={}, tenantId={}, fileType={}, ocrChars={}, errorType={}",
                    invoiceId, AiTenantContextHolder.getTenantId(), fileType, ocrText.length(),
                    ex.getClass().getSimpleName());
            InvoiceAiResult result = failedResult("Invoice model recognition failed");
            result.setRawJson(toRawJson(result, fileUrl, fileType, "model", ocrExtraction, null, ex.getClass().getSimpleName()));
            return result;
        }
    }

    private InvoiceAiResult parseModelResult(AiChatModelResponse response, String fileUrl, String fileType,
                                             OcrExtractionResult ocrExtraction) throws JsonProcessingException {
        JsonNode root = objectMapper.readTree(extractJsonObject(response.getContent()));
        InvoiceAiResult result = new InvoiceAiResult();
        result.setSupplierName(text(root, "supplierName"));
        result.setSupplierTaxNo(text(root, "supplierTaxNo"));
        result.setInvoiceNo(text(root, "invoiceNo"));
        result.setInvoiceDate(dateTime(root, "invoiceDate"));
        result.setDueDate(dateTime(root, "dueDate"));
        result.setCurrency(normalizeCurrency(text(root, "currency")));
        result.setNetAmount(decimal(root, "netAmount"));
        result.setVatAmount(decimal(root, "vatAmount"));
        result.setGrossAmount(decimal(root, "grossAmount"));
        result.setIban(text(root, "iban"));
        result.setBic(text(root, "bic"));
        result.setPaymentAccountName(text(root, "paymentAccountName"));
        result.setExpenseCategory(text(root, "expenseCategory"));
        result.setBusinessDesc(text(root, "businessDesc"));
        result.setPoNo(text(root, "poNo"));
        result.setContractNo(text(root, "contractNo"));
        result.setProjectName(text(root, "projectName"));
        result.setConfidence(decimal(root, "confidence"));
        result.setSummary(text(root, "summary"));
        fillMissingFieldsFromOcr(result, normalizeOcrText(ocrExtraction.content()));
        result.setRawJson(toRawJson(result, fileUrl, fileType, "model", ocrExtraction, response, null));
        return result;
    }

    private String buildSystemPrompt() {
        return """
                你是财务发票识别助手。请从 OCR 文本中抽取发票结构化字段。
                只输出一个 JSON 对象，不要输出 Markdown、解释、注释或额外文本。
                无法确认的字段填 null，不要编造。
                金额字段只输出数字，不带货币符号和千分位。
                日期字段统一输出 yyyy-MM-dd。
                currency 输出 ISO 4217 三位币种，例如 EUR、CNY、USD。
                confidence 输出 0 到 1 之间的小数。
                如果文档是付款申请表、付款通知或费用申请，不是标准税务发票，也要抽取可确认的付款字段。
                “付款单位”可作为 supplierName；“付款时间”可作为 dueDate；“金额”可作为 grossAmount。
                遇到 1273,72€、€ 1273,72 这类欧洲金额格式时，grossAmount 输出 1273.72，currency 输出 EUR。
                JSON 字段包括：supplierName、supplierTaxNo、invoiceNo、invoiceDate、dueDate、currency、netAmount、vatAmount、grossAmount、iban、bic、paymentAccountName、expenseCategory、businessDesc、poNo、contractNo、projectName、confidence、summary。
                """;
    }

    private String buildUserPrompt(String fileType, String ocrText) {
        return """
                文件类型：%s
                OCR 原文如下：
                %s
                """.formatted(fileType == null ? "" : fileType, ocrText);
    }

    private String extractJsonObject(String content) {
        if (content == null || content.isBlank()) {
            return "{}";
        }
        String text = content.trim();
        if (text.startsWith("```")) {
            int firstBrace = text.indexOf('{');
            int lastBrace = text.lastIndexOf('}');
            return firstBrace >= 0 && lastBrace > firstBrace ? text.substring(firstBrace, lastBrace + 1) : "{}";
        }
        int firstBrace = text.indexOf('{');
        int lastBrace = text.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            return text.substring(firstBrace, lastBrace + 1);
        }
        return text;
    }

    private InvoiceAiResult mockInvoice(Long invoiceId, String fileUrl, String fileType, OcrExtractionResult ocrExtraction,
                                        String provider) {
        int scenario = invoiceId == null ? 1 : (int) ((invoiceId - 1) % 3) + 1;
        InvoiceAiResult result = switch (scenario) {
            case 2 -> abcServiceInvoice();
            case 3 -> dhlDuplicateInvoice();
            default -> dhlLogisticsInvoice();
        };
        result.setRawJson(toRawJson(result, fileUrl, fileType, provider, ocrExtraction, null, null));
        return result;
    }

    private InvoiceAiResult dhlLogisticsInvoice() {
        InvoiceAiResult result = new InvoiceAiResult();
        result.setSupplierName("DHL Logistics GmbH");
        result.setInvoiceNo("INV-2026-001");
        result.setInvoiceDate(LocalDateTime.of(2026, 5, 10, 0, 0));
        result.setDueDate(LocalDateTime.of(2026, 6, 9, 0, 0));
        result.setCurrency("EUR");
        result.setNetAmount(new BigDecimal("2300.00"));
        result.setVatAmount(new BigDecimal("437.00"));
        result.setGrossAmount(new BigDecimal("2737.00"));
        result.setIban("DE00000000000000000000");
        result.setBic("COBADEFFXXX");
        result.setPaymentAccountName("DHL Logistics GmbH");
        result.setExpenseCategory("LOGISTICS");
        result.setBusinessDesc("Germany logistics service fee");
        result.setPoNo("PO-2026-001");
        result.setConfidence(new BigDecimal("0.91"));
        result.setSummary("本发票来自 DHL Logistics GmbH，发票号 INV-2026-001，含税金额 EUR 2,737.00，费用类别为物流。识别到 PO，未识别到合同号。");
        return result;
    }

    private InvoiceAiResult abcServiceInvoice() {
        InvoiceAiResult result = new InvoiceAiResult();
        result.setSupplierName("ABC Service GmbH");
        result.setInvoiceNo("RE-2026-045");
        result.setInvoiceDate(LocalDateTime.of(2026, 5, 18, 0, 0));
        result.setDueDate(LocalDateTime.of(2026, 6, 17, 0, 0));
        result.setCurrency("EUR");
        result.setNetAmount(new BigDecimal("6554.62"));
        result.setVatAmount(new BigDecimal("1245.38"));
        result.setGrossAmount(new BigDecimal("7800.00"));
        result.setIban("DE11111111111111111111");
        result.setBic("COBADEFFXXX");
        result.setPaymentAccountName("ABC Service GmbH");
        result.setExpenseCategory("SERVICE");
        result.setBusinessDesc("Consulting service fee");
        result.setConfidence(new BigDecimal("0.88"));
        result.setSummary("本发票来自 ABC Service GmbH，发票号 RE-2026-045，含税金额 EUR 7,800.00，未识别到 PO 和合同号。");
        return result;
    }

    private InvoiceAiResult dhlDuplicateInvoice() {
        InvoiceAiResult result = dhlLogisticsInvoice();
        result.setConfidence(new BigDecimal("0.90"));
        return result;
    }

    private OcrExtractionResult extractInvoiceText(Long invoiceId, String fileType, byte[] fileContent) {
        if (fileContent == null || fileContent.length == 0) {
            return OcrExtractionResult.skipped("empty-file");
        }
        String extension = normalizeFileType(fileType);
        DocumentParseContext context = DocumentParseContext.builder()
                .documentId(invoiceId)
                .tenantId(AiTenantContextHolder.getTenantId())
                .filename(invoiceId == null ? "invoice" : "invoice-" + invoiceId + "." + extension)
                .fileType(extension)
                .contentType(resolveContentType(extension))
                .build();
        try {
            if ("pdf".equals(extension)) {
                if (!ocrService.isEnabled()) {
                    return OcrExtractionResult.skipped("ocr-disabled");
                }
                try (PDDocument document = PDDocument.load(new ByteArrayInputStream(fileContent))) {
                    return OcrExtractionResult.success(ocrService.recognizePdf(document, context));
                }
            }
            if (FinanceInvoiceConstants.IMAGE_EXTENSIONS.contains(extension)) {
                if (!imageRecognitionService.isEnabled()) {
                    return OcrExtractionResult.skipped("ocr-disabled");
                }
                BufferedImage image = ImageIO.read(new ByteArrayInputStream(fileContent));
                if (image == null) {
                    return OcrExtractionResult.failed("image-decode-failed");
                }
                return OcrExtractionResult.success(imageRecognitionService.recognizeImage(image, context));
            }
            return OcrExtractionResult.skipped("unsupported-file-type");
        } catch (OcrException | IOException ex) {
            return OcrExtractionResult.failed(ex.getClass().getSimpleName());
        }
    }

    private String normalizeFileType(String fileType) {
        return fileType == null ? "" : fileType.trim().toLowerCase(Locale.ROOT);
    }

    private String resolveContentType(String extension) {
        return switch (extension) {
            case "pdf" -> "application/pdf";
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "tif", "tiff" -> "image/tiff";
            default -> "application/octet-stream";
        };
    }

    private String toRawJson(InvoiceAiResult result, String fileUrl, String fileType, String provider,
                             OcrExtractionResult ocrExtraction, AiChatModelResponse response, String errorType) {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("provider", provider);
        raw.put("fileUrl", fileUrl);
        raw.put("fileType", fileType);
        raw.put("ocrApplied", ocrExtraction.applied());
        raw.put("ocrSkippedReason", ocrExtraction.skippedReason());
        raw.put("ocrErrorType", ocrExtraction.errorType());
        raw.put("ocrCharCount", ocrExtraction.charCount());
        raw.put("ocrMetadata", ocrExtraction.metadata());
        if (response != null) {
            raw.put("model", response.getModel());
            raw.put("finishReason", response.getFinishReason());
            raw.put("promptTokens", response.getPromptTokens());
            raw.put("completionTokens", response.getCompletionTokens());
            raw.put("totalTokens", response.getTotalTokens());
        }
        if (errorType != null) {
            raw.put("modelErrorType", errorType);
        }
        raw.put("supplierName", result.getSupplierName());
        raw.put("invoiceNo", result.getInvoiceNo());
        raw.put("currency", result.getCurrency());
        raw.put("grossAmount", result.getGrossAmount());
        raw.put("confidence", result.getConfidence());
        try {
            return objectMapper.writeValueAsString(raw);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    private boolean isModelRecognitionEnabled() {
        String provider = aiProperties.getInvoice().getRecognitionProvider();
        return PROVIDER_MODEL.equalsIgnoreCase(provider)
                || "llm".equalsIgnoreCase(provider)
                || "openai-compatible".equalsIgnoreCase(provider);
    }

    private String truncateOcrText(String text) {
        int maxChars = resolveMaxOcrChars();
        if (text == null || text.length() <= maxChars) {
            return text == null ? "" : text;
        }
        return text.substring(0, maxChars) + "\n[OCR_TEXT_TRUNCATED]";
    }

    private String normalizeOcrText(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        String repaired = repairLatin1Mojibake(text);
        if (repaired == null) {
            return text;
        }
        int originalCjkCount = countCjkChars(text);
        int repairedCjkCount = countCjkChars(repaired);
        if (repairedCjkCount >= originalCjkCount + 5 && countMojibakeMarkers(text) > 0) {
            return repaired;
        }
        return text;
    }

    private void fillMissingFieldsFromOcr(InvoiceAiResult result, String ocrText) {
        if (result == null || ocrText == null || ocrText.isBlank()) {
            return;
        }
        if (result.getGrossAmount() == null) {
            result.setGrossAmount(extractAmount(ocrText));
        }
        if (result.getCurrency() == null) {
            result.setCurrency(extractCurrency(ocrText));
        }
        if (result.getDueDate() == null) {
            result.setDueDate(extractDateTime(ocrText));
        }
        if (result.getSupplierName() == null) {
            result.setSupplierName(extractSupplierName(ocrText));
        }
        if (result.getPaymentAccountName() == null) {
            result.setPaymentAccountName(result.getSupplierName());
        }
        if (result.getIban() == null) {
            result.setIban(extractIban(ocrText));
        }
        if (result.getBic() == null) {
            result.setBic(extractBic(ocrText));
        }
        if (result.getSummary() == null) {
            result.setSummary(buildRecognitionSummary(result));
        }
        if (result.getConfidence() == null) {
            result.setConfidence(new BigDecimal("0.80"));
        }
    }

    private BigDecimal extractAmount(String text) {
        Matcher matcher = AMOUNT_PATTERN.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        return parseAmount(matcher.group(1));
    }

    private BigDecimal parseAmount(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        String value = rawValue.trim().replaceAll("\\s+", "");
        int commaIndex = value.lastIndexOf(',');
        int dotIndex = value.lastIndexOf('.');
        if (commaIndex >= 0 && dotIndex >= 0) {
            value = commaIndex > dotIndex ? value.replace(".", "").replace(',', '.')
                    : value.replace(",", "");
        } else if (commaIndex >= 0) {
            value = value.replace(',', '.');
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String extractCurrency(String text) {
        String upper = text.toUpperCase(Locale.ROOT);
        if (upper.contains("EUR") || text.contains("€")) {
            return "EUR";
        }
        if (upper.contains("CNY") || upper.contains("RMB") || text.contains("￥")) {
            return "CNY";
        }
        if (upper.contains("USD") || text.contains("$")) {
            return "USD";
        }
        return null;
    }

    private LocalDateTime extractDateTime(String text) {
        Matcher matcher = DUE_DATE_PATTERN.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        String value = matcher.group(1)
                .replace('年', '-')
                .replace('月', '-')
                .replace("日", "")
                .replace('/', '-')
                .replace('.', '-');
        try {
            return LocalDate.parse(value).atStartOfDay();
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private String extractSupplierName(String text) {
        Matcher matcher = SUPPLIER_PATTERN.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        String value = matcher.group(1).trim();
        return value.isEmpty() ? null : value;
    }

    private String extractIban(String text) {
        Matcher matcher = IBAN_PATTERN.matcher(text.toUpperCase(Locale.ROOT));
        while (matcher.find()) {
            String value = matcher.group().replaceAll("\\s+", "");
            if (value.length() >= 15 && value.length() <= 34) {
                return value;
            }
        }
        return null;
    }

    private String extractBic(String text) {
        Matcher matcher = BIC_PATTERN.matcher(text.toUpperCase(Locale.ROOT));
        return matcher.find() ? matcher.group() : null;
    }

    private String buildRecognitionSummary(InvoiceAiResult result) {
        String supplierName = result.getSupplierName() == null ? "未知供应商" : result.getSupplierName();
        StringBuilder builder = new StringBuilder("识别到付款/发票资料，供应商为 ").append(supplierName);
        if (result.getGrossAmount() != null) {
            builder.append("，金额为 ");
            if (result.getCurrency() != null) {
                builder.append(result.getCurrency()).append(' ');
            }
            builder.append(result.getGrossAmount());
        }
        if (result.getDueDate() != null) {
            builder.append("，付款/到期日期为 ").append(result.getDueDate().toLocalDate());
        }
        builder.append('。');
        return builder.toString();
    }

    private String repairLatin1Mojibake(String text) {
        return new String(text.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
    }

    private int countCjkChars(String text) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            Character.UnicodeScript script = Character.UnicodeScript.of(text.charAt(i));
            if (script == Character.UnicodeScript.HAN) {
                count++;
            }
        }
        return count;
    }

    private int countMojibakeMarkers(String text) {
        int count = 0;
        String markers = "äåæçèéöüÄÅÆÇÈÉÂÃ";
        for (int i = 0; i < text.length(); i++) {
            if (markers.indexOf(text.charAt(i)) >= 0) {
                count++;
            }
        }
        return count;
    }

    private int resolveMaxOcrChars() {
        Integer value = aiProperties.getInvoice().getRecognitionMaxOcrChars();
        if (value == null || value <= 0) {
            return DEFAULT_MAX_OCR_CHARS;
        }
        return Math.min(value, 50000);
    }

    private int resolveMaxTokens() {
        Integer value = aiProperties.getInvoice().getRecognitionMaxTokens();
        if (value == null || value <= 0) {
            return DEFAULT_MAX_TOKENS;
        }
        return value;
    }

    private InvoiceAiResult failedResult(String message) {
        InvoiceAiResult result = new InvoiceAiResult();
        result.setErrorMessage(message);
        result.setConfidence(BigDecimal.ZERO);
        result.setSummary(message);
        return result;
    }

    private String text(JsonNode root, String fieldName) {
        JsonNode node = root.path(fieldName);
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.asText(null);
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty() || "null".equalsIgnoreCase(trimmed) || "unknown".equalsIgnoreCase(trimmed)
                || "未明确".equals(trimmed) || "无法确认".equals(trimmed)) {
            return null;
        }
        return trimmed;
    }

    private BigDecimal decimal(JsonNode root, String fieldName) {
        JsonNode node = root.path(fieldName);
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.decimalValue();
        }
        String value = text(root, fieldName);
        if (value == null) {
            return null;
        }
        String normalized = value.replace(",", "")
                .replace("€", "")
                .replace("￥", "")
                .replace("¥", "")
                .replace("$", "")
                .replaceAll("\\s+", "");
        try {
            return new BigDecimal(normalized);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private LocalDateTime dateTime(JsonNode root, String fieldName) {
        String value = text(root, fieldName);
        if (value == null) {
            return null;
        }
        String normalized = value.replace('/', '-');
        try {
            return LocalDate.parse(normalized).atStartOfDay();
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDateTime.parse(normalized);
            } catch (DateTimeParseException ignoredAgain) {
                return null;
            }
        }
    }

    private String normalizeCurrency(String currency) {
        if (currency == null) {
            return null;
        }
        String normalized = currency.trim().toUpperCase(Locale.ROOT);
        return normalized.length() > 8 ? normalized.substring(0, 8) : normalized;
    }

    private String blankToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private record OcrExtractionResult(boolean applied, String skippedReason, String errorType, int charCount,
                                       Map<String, Object> metadata, String content) {

        private static OcrExtractionResult success(OcrResult result) {
            String content = result == null ? null : result.getContent();
            Map<String, Object> metadata = result == null || result.getMetadata() == null
                    ? Map.of() : result.getMetadata();
            return new OcrExtractionResult(true, null, null, content == null ? 0 : content.length(), metadata, content);
        }

        private static OcrExtractionResult skipped(String reason) {
            return new OcrExtractionResult(false, reason, null, 0, Map.of(), null);
        }

        private static OcrExtractionResult failed(String errorType) {
            return new OcrExtractionResult(false, null, errorType, 0, Map.of(), null);
        }

    }

}
