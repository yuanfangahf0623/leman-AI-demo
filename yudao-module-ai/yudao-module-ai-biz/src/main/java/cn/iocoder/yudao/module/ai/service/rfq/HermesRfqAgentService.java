package cn.iocoder.yudao.module.ai.service.rfq;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.ai.framework.rfq.AiRfqProperties;
import cn.iocoder.yudao.module.ai.framework.tenant.AiTenantContextHolder;
import cn.iocoder.yudao.module.ai.service.chatmodel.AiChatModelRequest;
import cn.iocoder.yudao.module.ai.service.chatmodel.AiChatModelResponse;
import cn.iocoder.yudao.module.ai.service.chatmodel.AiChatModelService;
import cn.iocoder.yudao.module.ai.service.rfq.dto.EmailDTO;
import cn.iocoder.yudao.module.ai.service.rfq.dto.HermesRfqDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.module.ai.enums.RfqErrorCodeConstants.RFQ_HERMES_REQUEST_FAILED;
import static cn.iocoder.yudao.module.ai.enums.RfqErrorCodeConstants.RFQ_HERMES_RESPONSE_INVALID;

/**
 * Hermes RFQ agent adapter.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HermesRfqAgentService {

    private static final String DEFAULT_SYSTEM_PROMPT = """
            You are Hermes, an RFQ extraction agent.
            Return exactly one strict JSON object and no markdown or extra text.
            Required fields: is_rfq, confidence, customer, products, risk_score, missing_info, next_actions.
            Use null or empty arrays when information is unavailable. Do not invent facts.
            """;

    private final AiRfqProperties properties;
    private final AiChatModelService aiChatModelService;
    private final ObjectMapper objectMapper;

    public HermesRfqDTO extractRfq(EmailDTO emailDTO) {
        return extractRfq(emailDTO, List.of());
    }

    public HermesRfqDTO extractRfq(EmailDTO emailDTO, List<String> ragContext) {
        validateEmail(emailDTO);
        String userPrompt = buildUserPrompt(emailDTO, ragContext);
        AiChatModelRequest request = AiChatModelRequest.builder()
                .model(blankToNull(properties.getHermes().getModel()))
                .systemPrompt(resolveSystemPrompt())
                .userPrompt(limit(userPrompt, resolveMaxInputChars()))
                .temperature(0D)
                .maxTokens(resolveMaxTokens())
                .metadata(Map.of(
                        "tenantId", AiTenantContextHolder.getTenantId(),
                        "callType", "RFQ_HERMES",
                        "responseFormat", "json_object"))
                .build();
        AiChatModelResponse response;
        try {
            response = aiChatModelService.chat(request);
        } catch (Exception ex) {
            log.warn("Hermes RFQ model call failed, tenantId={}, subject={}, errorType={}",
                    AiTenantContextHolder.getTenantId(), safeSubject(emailDTO.getSubject()), ex.getClass().getSimpleName());
            throw new ServiceException(RFQ_HERMES_REQUEST_FAILED, "Hermes RFQ model call failed");
        }
        HermesRfqDTO result = parseStrictJson(response == null ? null : response.getContent());
        log.info("Hermes RFQ model call success, tenantId={}, isRfq={}, confidence={}, promptTokens={}, completionTokens={}",
                AiTenantContextHolder.getTenantId(), result.getIsRfq(), result.getConfidence(),
                response.getPromptTokens(), response.getCompletionTokens());
        return result;
    }

    private HermesRfqDTO parseStrictJson(String content) {
        String json = requireStrictJson(content);
        try {
            JsonNode root = objectMapper.readTree(json);
            validateSchema(root);
            if (!root.path("is_rfq").asBoolean()) {
                HermesRfqDTO dto = new HermesRfqDTO();
                dto.setIsRfq(false);
                dto.setConfidence(optionalNumber(root, "confidence"));
                dto.setRiskScore(optionalNumber(root, "risk_score"));
                dto.setMissingInfo(optionalStringArray(root, "missing_info"));
                dto.setNextActions(optionalStringArray(root, "next_actions"));
                dto.setProducts(List.of());
                dto.setRawJson(json);
                return dto;
            }
            HermesRfqDTO dto = buildRfqDto(root, json);
            dto.setRawJson(json);
            return dto;
        } catch (JsonProcessingException ex) {
            throw new ServiceException(RFQ_HERMES_RESPONSE_INVALID, "Hermes RFQ JSON parse failed");
        }
    }

    private void validateSchema(JsonNode root) {
        if (root == null || !root.isObject()) {
            throw new ServiceException(RFQ_HERMES_RESPONSE_INVALID, "Hermes RFQ response must be JSON object");
        }
        requireBoolean(root, "is_rfq");
        if (!root.path("is_rfq").asBoolean()) {
            return;
        }
        requireNumberInRange(root, "confidence", BigDecimal.ZERO, BigDecimal.ONE);
        requireNumberInRange(root, "risk_score", BigDecimal.ZERO, new BigDecimal("100"));
        requireArray(root, "products");
        requireArray(root, "missing_info");
        requireArray(root, "next_actions");
        if (!StringUtils.hasText(root.path("customer").asText(null))) {
            throw new ServiceException(RFQ_HERMES_RESPONSE_INVALID, "Hermes RFQ customer is required");
        }
        JsonNode products = root.path("products");
        if (products.isEmpty()) {
            throw new ServiceException(RFQ_HERMES_RESPONSE_INVALID, "Hermes RFQ products are required");
        }
        for (JsonNode product : products) {
            if (product.isTextual()) {
                if (!StringUtils.hasText(product.asText())) {
                    throw new ServiceException(RFQ_HERMES_RESPONSE_INVALID, "Hermes RFQ product text is empty");
                }
                continue;
            }
            if (product.isObject()) {
                String name = product.path("name").asText(null);
                String rawText = product.path("raw_text").asText(null);
                if (StringUtils.hasText(name) || StringUtils.hasText(rawText)) {
                    continue;
                }
                throw new ServiceException(RFQ_HERMES_RESPONSE_INVALID, "Hermes RFQ product name is required");
            }
            throw new ServiceException(RFQ_HERMES_RESPONSE_INVALID, "Hermes RFQ product field is invalid");
        }
    }

    private HermesRfqDTO buildRfqDto(JsonNode root, String json) {
        HermesRfqDTO dto = new HermesRfqDTO();
        dto.setIsRfq(root.path("is_rfq").asBoolean());
        dto.setConfidence(optionalNumber(root, "confidence"));
        dto.setCustomer(blankToNull(root.path("customer").asText(null)));
        dto.setProducts(parseProducts(root.path("products")));
        dto.setRiskScore(optionalNumber(root, "risk_score"));
        dto.setMissingInfo(optionalStringArray(root, "missing_info"));
        dto.setNextActions(optionalStringArray(root, "next_actions"));
        dto.setRawJson(json);
        return dto;
    }

    private List<HermesRfqDTO.Product> parseProducts(JsonNode products) {
        if (products == null || !products.isArray() || products.isEmpty()) {
            return List.of();
        }
        List<HermesRfqDTO.Product> result = new ArrayList<>(products.size());
        for (JsonNode product : products) {
            if (product.isTextual()) {
                String rawText = product.asText().trim();
                if (StringUtils.hasText(rawText)) {
                    result.add(HermesRfqDTO.Product.builder().rawText(rawText).build());
                }
                continue;
            }
            if (product.isObject()) {
                result.add(HermesRfqDTO.Product.builder()
                        .name(blankToNull(product.path("name").asText(null)))
                        .quantity(blankToNull(product.path("quantity").asText(null)))
                        .unit(blankToNull(product.path("unit").asText(null)))
                        .specifications(blankToNull(product.path("specifications").asText(null)))
                        .rawText(blankToNull(product.path("raw_text").asText(null)))
                        .build());
            }
        }
        return result;
    }

    private String requireStrictJson(String content) {
        if (!StringUtils.hasText(content)) {
            throw new ServiceException(RFQ_HERMES_RESPONSE_INVALID, "Hermes RFQ response is empty");
        }
        String text = content.trim();
        if (!text.startsWith("{") || !text.endsWith("}")) {
            throw new ServiceException(RFQ_HERMES_RESPONSE_INVALID, "Hermes RFQ response is not strict JSON");
        }
        return text;
    }

    private void requireBoolean(JsonNode root, String fieldName) {
        if (!root.path(fieldName).isBoolean()) {
            throw new ServiceException(RFQ_HERMES_RESPONSE_INVALID, "Hermes RFQ boolean field is invalid");
        }
    }

    private void requireArray(JsonNode root, String fieldName) {
        if (!root.path(fieldName).isArray()) {
            throw new ServiceException(RFQ_HERMES_RESPONSE_INVALID, "Hermes RFQ array field is invalid");
        }
    }

    private void requireNumberInRange(JsonNode root, String fieldName, BigDecimal min, BigDecimal max) {
        JsonNode node = root.path(fieldName);
        if (!node.isNumber()) {
            throw new ServiceException(RFQ_HERMES_RESPONSE_INVALID, "Hermes RFQ number field is invalid");
        }
        BigDecimal value = node.decimalValue();
        if (value.compareTo(min) < 0 || value.compareTo(max) > 0) {
            throw new ServiceException(RFQ_HERMES_RESPONSE_INVALID, "Hermes RFQ number field is out of range");
        }
    }

    private BigDecimal optionalNumber(JsonNode root, String fieldName) {
        JsonNode node = root.path(fieldName);
        return node.isNumber() ? node.decimalValue() : null;
    }

    private List<String> optionalStringArray(JsonNode root, String fieldName) {
        JsonNode node = root.path(fieldName);
        if (!node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>(node.size());
        for (JsonNode item : node) {
            if (item.isTextual() && StringUtils.hasText(item.asText())) {
                values.add(item.asText().trim());
            }
        }
        return values;
    }

    private String buildUserPrompt(EmailDTO emailDTO, List<String> ragContext) {
        StringBuilder builder = new StringBuilder();
        appendLine(builder, "from", emailDTO.getFrom());
        appendLine(builder, "to", emailDTO.getTo());
        appendLine(builder, "subject", emailDTO.getSubject());
        appendLine(builder, "receivedTime", emailDTO.getReceivedTime());
        builder.append("\nbodyText:\n").append(firstText(emailDTO.getBodyText(), "")).append('\n');
        if (emailDTO.getAttachments() != null && !emailDTO.getAttachments().isEmpty()) {
            builder.append("\nattachments:\n");
            for (EmailDTO.Attachment attachment : emailDTO.getAttachments()) {
                builder.append("- fileName: ").append(firstText(attachment.getFileName(), "")).append('\n');
                builder.append("  contentType: ").append(firstText(attachment.getContentType(), "")).append('\n');
                builder.append("  text:\n").append(firstText(attachment.getText(), "")).append('\n');
            }
        }
        if (ragContext != null && !ragContext.isEmpty()) {
            builder.append("\nragContext:\n");
            for (String item : ragContext) {
                if (StringUtils.hasText(item)) {
                    builder.append("- ").append(item.trim()).append('\n');
                }
            }
        }
        return builder.toString();
    }

    private void validateEmail(EmailDTO emailDTO) {
        if (emailDTO == null || (!StringUtils.hasText(emailDTO.getBodyText())
                && (emailDTO.getAttachments() == null || emailDTO.getAttachments().isEmpty()))) {
            throw new ServiceException(RFQ_HERMES_REQUEST_FAILED, "EmailDTO content is empty");
        }
    }

    private String resolveSystemPrompt() {
        String prompt = properties.getHermes() == null ? null : properties.getHermes().getSystemPrompt();
        return StringUtils.hasText(prompt) ? prompt.trim() : DEFAULT_SYSTEM_PROMPT;
    }

    private int resolveMaxInputChars() {
        Integer value = properties.getHermes() == null ? null : properties.getHermes().getMaxInputChars();
        return value == null || value <= 0 ? 24_000 : Math.min(value, 100_000);
    }

    private int resolveMaxTokens() {
        Integer value = properties.getHermes() == null ? null : properties.getHermes().getMaxTokens();
        return value == null || value <= 0 ? 2_000 : value;
    }

    private List<String> safeStringList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>(values.size());
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                result.add(value.trim());
            }
        }
        return result;
    }

    private void appendLine(StringBuilder builder, String key, Object value) {
        if (value != null) {
            builder.append(key).append(": ").append(value).append('\n');
        }
    }

    private String firstText(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    private String limit(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String safeSubject(String subject) {
        if (subject == null) {
            return "";
        }
        return subject.length() <= 128 ? subject : subject.substring(0, 128);
    }

}
