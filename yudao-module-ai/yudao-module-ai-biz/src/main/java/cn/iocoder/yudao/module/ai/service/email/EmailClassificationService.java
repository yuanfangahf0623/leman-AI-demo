package cn.iocoder.yudao.module.ai.service.email;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.ai.framework.tenant.AiTenantContextHolder;
import cn.iocoder.yudao.module.ai.service.chatmodel.AiChatModelRequest;
import cn.iocoder.yudao.module.ai.service.chatmodel.AiChatModelResponse;
import cn.iocoder.yudao.module.ai.service.chatmodel.AiChatModelService;
import cn.iocoder.yudao.module.ai.service.email.dto.EmailClassificationDTO;
import cn.iocoder.yudao.module.ai.service.email.dto.EmailClassificationRequest;
import cn.iocoder.yudao.module.ai.service.rfq.dto.EmailDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static cn.iocoder.yudao.module.ai.enums.AiEmailClassificationErrorCodeConstants.EMAIL_CLASSIFICATION_REQUEST_FAILED;
import static cn.iocoder.yudao.module.ai.enums.AiEmailClassificationErrorCodeConstants.EMAIL_CLASSIFICATION_RESPONSE_INVALID;

/**
 * Common LLM-based email classification service.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailClassificationService {

    private static final String DEFAULT_CALL_TYPE = "EMAIL_CLASSIFICATION";
    private static final int DEFAULT_MAX_INPUT_CHARS = 24_000;
    private static final int DEFAULT_MAX_TOKENS = 800;

    private final AiChatModelService aiChatModelService;
    private final ObjectMapper objectMapper;

    public EmailClassificationDTO classify(EmailDTO emailDTO, EmailClassificationRequest request) {
        validateEmail(emailDTO);
        ClassificationDefinition definition = validateRequest(request);
        AiChatModelRequest chatRequest = AiChatModelRequest.builder()
                .model(blankToNull(request.getModel()))
                .systemPrompt(buildSystemPrompt(request, definition))
                .userPrompt(limit(buildUserPrompt(emailDTO), resolveMaxInputChars(request)))
                .temperature(0D)
                .maxTokens(resolveMaxTokens(request))
                .metadata(Map.of(
                        "tenantId", AiTenantContextHolder.getTenantId(),
                        "callType", firstText(request.getCallType(), DEFAULT_CALL_TYPE),
                        "responseFormat", "json_object"))
                .build();
        AiChatModelResponse response;
        try {
            response = aiChatModelService.chat(chatRequest);
        } catch (Exception ex) {
            log.warn("Email classification model call failed, tenantId={}, callType={}, subject={}, errorType={}",
                    AiTenantContextHolder.getTenantId(), firstText(request.getCallType(), DEFAULT_CALL_TYPE),
                    safeSubject(emailDTO.getSubject()), ex.getClass().getSimpleName());
            throw new ServiceException(EMAIL_CLASSIFICATION_REQUEST_FAILED, "Email classification model call failed");
        }
        EmailClassificationDTO result = parseStrictJson(response == null ? null : response.getContent(), definition);
        log.info("Email classification success, tenantId={}, callType={}, type={}, isMatch={}, confidence={}, promptTokens={}, completionTokens={}",
                AiTenantContextHolder.getTenantId(), firstText(request.getCallType(), DEFAULT_CALL_TYPE),
                result.getClassificationType(), result.getMatch(), result.getConfidence(),
                response.getPromptTokens(), response.getCompletionTokens());
        return result;
    }

    private EmailClassificationDTO parseStrictJson(String content, ClassificationDefinition definition) {
        String json = requireStrictJson(content);
        try {
            JsonNode root = objectMapper.readTree(json);
            validateSchema(root, definition);
            EmailClassificationDTO dto = objectMapper.treeToValue(root, EmailClassificationDTO.class);
            dto.setRiskFlags(optionalStringArray(root, "risk_flags"));
            dto.setRawJson(json);
            return dto;
        } catch (JsonProcessingException ex) {
            throw new ServiceException(EMAIL_CLASSIFICATION_RESPONSE_INVALID, "Email classification JSON parse failed");
        }
    }

    private void validateSchema(JsonNode root, ClassificationDefinition definition) {
        if (root == null || !root.isObject()) {
            throw new ServiceException(EMAIL_CLASSIFICATION_RESPONSE_INVALID, "Email classification response must be JSON object");
        }
        if (!root.path("is_match").isBoolean()) {
            throw new ServiceException(EMAIL_CLASSIFICATION_RESPONSE_INVALID, "Email classification is_match is invalid");
        }
        String classificationType = root.path("classification_type").asText(null);
        if (!definition.allowedTypes.contains(classificationType)) {
            throw new ServiceException(EMAIL_CLASSIFICATION_RESPONSE_INVALID, "Email classification type is invalid");
        }
        JsonNode confidence = root.path("confidence");
        if (!confidence.isNumber()) {
            throw new ServiceException(EMAIL_CLASSIFICATION_RESPONSE_INVALID, "Email classification confidence is invalid");
        }
        BigDecimal confidenceValue = confidence.decimalValue();
        if (confidenceValue.compareTo(BigDecimal.ZERO) < 0 || confidenceValue.compareTo(BigDecimal.ONE) > 0) {
            throw new ServiceException(EMAIL_CLASSIFICATION_RESPONSE_INVALID, "Email classification confidence is out of range");
        }
        boolean isMatch = root.path("is_match").asBoolean();
        boolean positiveType = definition.positiveTypes.contains(classificationType);
        if (isMatch != positiveType) {
            throw new ServiceException(EMAIL_CLASSIFICATION_RESPONSE_INVALID, "Email classification type conflicts with is_match");
        }
        if (!root.path("risk_flags").isArray()) {
            throw new ServiceException(EMAIL_CLASSIFICATION_RESPONSE_INVALID, "Email classification risk_flags is invalid");
        }
    }

    private ClassificationDefinition validateRequest(EmailClassificationRequest request) {
        if (request == null || !StringUtils.hasText(request.getSystemPrompt())) {
            throw new ServiceException(EMAIL_CLASSIFICATION_REQUEST_FAILED, "Email classification prompt is required");
        }
        if (CollectionUtils.isEmpty(request.getAllowedTypes())) {
            throw new ServiceException(EMAIL_CLASSIFICATION_REQUEST_FAILED, "Email classification allowed types are required");
        }
        Set<String> allowedTypes = normalizeTypeSet(request.getAllowedTypes());
        Set<String> positiveTypes = normalizeTypeSet(request.getPositiveTypes());
        if (positiveTypes.isEmpty() || !allowedTypes.containsAll(positiveTypes)) {
            throw new ServiceException(EMAIL_CLASSIFICATION_REQUEST_FAILED, "Email classification positive types are invalid");
        }
        String nonMatchType = firstText(request.getNonMatchType(), "");
        if (!allowedTypes.contains(nonMatchType) || positiveTypes.contains(nonMatchType)) {
            throw new ServiceException(EMAIL_CLASSIFICATION_REQUEST_FAILED, "Email classification non-match type is invalid");
        }
        return new ClassificationDefinition(allowedTypes, positiveTypes, nonMatchType);
    }

    private String buildSystemPrompt(EmailClassificationRequest request, ClassificationDefinition definition) {
        StringBuilder builder = new StringBuilder();
        builder.append(request.getSystemPrompt().trim()).append("\n\n");
        builder.append("Return exactly one strict JSON object and no markdown.\n");
        builder.append("Required fields: is_match, classification_type, confidence, reason, risk_flags.\n");
        builder.append("classification_type must be one of: ")
                .append(String.join(", ", definition.allowedTypes)).append(".\n");
        builder.append("is_match must be true only for: ")
                .append(String.join(", ", definition.positiveTypes)).append(".\n");
        builder.append("Use classification_type=").append(definition.nonMatchType)
                .append(" and is_match=false for all other emails.\n");
        return builder.toString();
    }

    private String buildUserPrompt(EmailDTO emailDTO) {
        StringBuilder builder = new StringBuilder();
        appendLine(builder, "from", emailDTO.getFrom());
        appendLine(builder, "to", emailDTO.getTo());
        appendLine(builder, "subject", emailDTO.getSubject());
        appendLine(builder, "receivedTime", emailDTO.getReceivedTime());
        builder.append("\nbodyText:\n").append(firstText(emailDTO.getBodyText(), "")).append('\n');
        if (emailDTO.getAttachments() != null && !emailDTO.getAttachments().isEmpty()) {
            builder.append("\nattachments:\n");
            for (EmailDTO.Attachment attachment : emailDTO.getAttachments()) {
                if (attachment == null) {
                    continue;
                }
                builder.append("- fileName: ").append(firstText(attachment.getFileName(), "")).append('\n');
                builder.append("  contentType: ").append(firstText(attachment.getContentType(), "")).append('\n');
                builder.append("  text:\n").append(firstText(attachment.getText(), "")).append('\n');
            }
        }
        return builder.toString();
    }

    private String requireStrictJson(String content) {
        if (!StringUtils.hasText(content)) {
            throw new ServiceException(EMAIL_CLASSIFICATION_RESPONSE_INVALID, "Email classification response is empty");
        }
        String text = content.trim();
        if (!text.startsWith("{") || !text.endsWith("}")) {
            throw new ServiceException(EMAIL_CLASSIFICATION_RESPONSE_INVALID, "Email classification response is not strict JSON");
        }
        return text;
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

    private void validateEmail(EmailDTO emailDTO) {
        if (emailDTO == null || (!StringUtils.hasText(emailDTO.getBodyText())
                && (emailDTO.getAttachments() == null || emailDTO.getAttachments().isEmpty()))) {
            throw new ServiceException(EMAIL_CLASSIFICATION_REQUEST_FAILED, "EmailDTO content is empty");
        }
    }

    private Set<String> normalizeTypeSet(List<String> types) {
        Set<String> result = new LinkedHashSet<>();
        if (types == null) {
            return result;
        }
        for (String type : types) {
            if (StringUtils.hasText(type)) {
                result.add(type.trim());
            }
        }
        return result;
    }

    private int resolveMaxInputChars(EmailClassificationRequest request) {
        Integer value = request.getMaxInputChars();
        return value == null || value <= 0 ? DEFAULT_MAX_INPUT_CHARS : Math.min(value, 100_000);
    }

    private int resolveMaxTokens(EmailClassificationRequest request) {
        Integer value = request.getMaxTokens();
        return value == null || value <= 0 ? DEFAULT_MAX_TOKENS : value;
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

    private record ClassificationDefinition(Set<String> allowedTypes, Set<String> positiveTypes, String nonMatchType) {
    }

}
