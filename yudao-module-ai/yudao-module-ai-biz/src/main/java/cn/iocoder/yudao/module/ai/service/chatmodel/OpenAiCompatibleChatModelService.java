package cn.iocoder.yudao.module.ai.service.chatmodel;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.ai.enums.ChatMessageRoleEnum;
import cn.iocoder.yudao.module.ai.framework.config.AiProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.module.ai.enums.AiChatModelErrorCodeConstants.CHAT_MODEL_CONFIG_INVALID;
import static cn.iocoder.yudao.module.ai.enums.AiChatModelErrorCodeConstants.CHAT_MODEL_REQUEST_FAILED;
import static cn.iocoder.yudao.module.ai.enums.AiChatModelErrorCodeConstants.CHAT_MODEL_REQUEST_INVALID;
import static cn.iocoder.yudao.module.ai.enums.AiChatModelErrorCodeConstants.CHAT_MODEL_RESPONSE_INVALID;

/**
 * OpenAI 兼容聊天模型服务实现。
 *
 * <p>API Key、Base URL、模型名称均从 {@link AiProperties} 注入，启动阶段不校验配置，实际调用时再校验。</p>
 */
@Slf4j
public class OpenAiCompatibleChatModelService implements AiChatModelService {

    private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";
    private static final int DEFAULT_CONNECT_TIMEOUT_SECONDS = 10;
    private static final int DEFAULT_READ_TIMEOUT_SECONDS = 60;
    private static final int HTTP_SUCCESS_MIN = 200;
    private static final int HTTP_SUCCESS_MAX = 299;

    private final AiProperties.ModelProperties modelProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public OpenAiCompatibleChatModelService(AiProperties aiProperties) {
        this(aiProperties, new ObjectMapper());
    }

    public OpenAiCompatibleChatModelService(AiProperties aiProperties, ObjectMapper objectMapper) {
        this.modelProperties = aiProperties.getModel() == null ? new AiProperties.ModelProperties() : aiProperties.getModel();
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(toDuration(modelProperties.getConnectTimeoutSeconds(), DEFAULT_CONNECT_TIMEOUT_SECONDS))
                .build();
    }

    @Override
    public AiChatModelResponse chat(AiChatModelRequest request) {
        URI chatUri = buildChatCompletionsUri();
        String apiKey = requiredConfig(modelProperties.getApiKey(), "api-key");
        String chatModel = resolveChatModel(request);
        List<AiChatModelMessage> messages = buildMessages(request);
        String requestBody = buildRequestBody(request, chatModel, messages);
        HttpRequest httpRequest = HttpRequest.newBuilder(chatUri)
                .timeout(toDuration(modelProperties.getReadTimeoutSeconds(), DEFAULT_READ_TIMEOUT_SECONDS))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();

        long startNanos = System.nanoTime();
        try {
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            long elapsedMs = elapsedMillis(startNanos);
            if (response.statusCode() < HTTP_SUCCESS_MIN || response.statusCode() > HTTP_SUCCESS_MAX) {
                log.warn("OpenAI compatible chat call failed, endpoint={}, model={}, messageCount={}, status={}, elapsedMs={}, apiKey={}",
                        sanitizeEndpoint(chatUri), chatModel, messages.size(), response.statusCode(), elapsedMs, maskApiKey(apiKey));
                throw new ServiceException(CHAT_MODEL_REQUEST_FAILED, "Chat Model 外部调用失败");
            }
            AiChatModelResponse chatResponse;
            try {
                chatResponse = parseResponse(response.body(), chatModel);
            } catch (ServiceException ex) {
                log.warn("OpenAI compatible chat response invalid, endpoint={}, model={}, messageCount={}, elapsedMs={}, apiKey={}, code={}",
                        sanitizeEndpoint(chatUri), chatModel, messages.size(), elapsedMs, maskApiKey(apiKey), ex.getCode());
                throw ex;
            }
            log.info("OpenAI compatible chat call success, endpoint={}, model={}, messageCount={}, elapsedMs={}",
                    sanitizeEndpoint(chatUri), chatResponse.getModel(), messages.size(), elapsedMs);
            return chatResponse;
        } catch (HttpTimeoutException ex) {
            logCallException(chatUri, chatModel, messages.size(), startNanos, apiKey, "timeout", ex);
            throw new ServiceException(CHAT_MODEL_REQUEST_FAILED, "Chat Model 外部调用超时");
        } catch (IOException ex) {
            logCallException(chatUri, chatModel, messages.size(), startNanos, apiKey, "io", ex);
            throw new ServiceException(CHAT_MODEL_REQUEST_FAILED, "Chat Model 外部调用失败");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logCallException(chatUri, chatModel, messages.size(), startNanos, apiKey, "interrupted", ex);
            throw new ServiceException(CHAT_MODEL_REQUEST_FAILED, "Chat Model 外部调用被中断");
        }
    }

    public String getChatModel() {
        return modelProperties.getChatModel();
    }

    private URI buildChatCompletionsUri() {
        String baseUrl = requiredConfig(modelProperties.getBaseUrl(), "base-url");
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
            log.warn("OpenAI compatible chat config invalid, field=base-url, errorType={}, error={}",
                    ex.getClass().getSimpleName(), ex.getMessage());
            throw new ServiceException(CHAT_MODEL_CONFIG_INVALID, "Chat Model 服务地址配置非法");
        }
    }

    private String resolveChatModel(AiChatModelRequest request) {
        if (request != null && hasText(request.getModel())) {
            return request.getModel().trim();
        }
        return requiredConfig(modelProperties.getChatModel(), "chat-model");
    }

    private List<AiChatModelMessage> buildMessages(AiChatModelRequest request) {
        if (request == null) {
            throw new ServiceException(CHAT_MODEL_REQUEST_INVALID, "Chat Model 请求不能为空");
        }
        if (request.getMessages() != null && !request.getMessages().isEmpty()) {
            return normalizeMessages(request.getMessages());
        }
        List<AiChatModelMessage> messages = new ArrayList<>(2);
        if (hasText(request.getSystemPrompt())) {
            messages.add(AiChatModelMessage.builder()
                    .role(ChatMessageRoleEnum.SYSTEM.getCode())
                    .content(request.getSystemPrompt().trim())
                    .build());
        }
        if (hasText(request.getUserPrompt())) {
            messages.add(AiChatModelMessage.builder()
                    .role(ChatMessageRoleEnum.USER.getCode())
                    .content(request.getUserPrompt().trim())
                    .build());
        }
        if (messages.isEmpty()) {
            throw new ServiceException(CHAT_MODEL_REQUEST_INVALID, "Chat Model 消息不能为空");
        }
        return messages;
    }

    private List<AiChatModelMessage> normalizeMessages(List<AiChatModelMessage> messages) {
        List<AiChatModelMessage> normalizedMessages = new ArrayList<>(messages.size());
        for (AiChatModelMessage message : messages) {
            if (message == null || !hasText(message.getRole())) {
                throw new ServiceException(CHAT_MODEL_REQUEST_INVALID, "Chat Model 消息角色不能为空");
            }
            normalizedMessages.add(AiChatModelMessage.builder()
                    .role(message.getRole().trim())
                    .content(message.getContent() == null ? "" : message.getContent())
                    .build());
        }
        return normalizedMessages;
    }

    private String buildRequestBody(AiChatModelRequest request, String chatModel, List<AiChatModelMessage> messages) {
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", chatModel);
        requestBody.put("messages", toMessagePayload(messages));
        if (request != null && request.getTemperature() != null) {
            requestBody.put("temperature", request.getTemperature());
        }
        if (request != null && request.getMaxTokens() != null) {
            requestBody.put("max_tokens", request.getMaxTokens());
        }
        try {
            return objectMapper.writeValueAsString(requestBody);
        } catch (JsonProcessingException ex) {
            throw new ServiceException(CHAT_MODEL_REQUEST_FAILED, "Chat Model 请求构造失败");
        }
    }

    private List<Map<String, String>> toMessagePayload(List<AiChatModelMessage> messages) {
        List<Map<String, String>> payload = new ArrayList<>(messages.size());
        for (AiChatModelMessage message : messages) {
            Map<String, String> item = new LinkedHashMap<>(2);
            item.put("role", message.getRole());
            item.put("content", message.getContent());
            payload.add(item);
        }
        return payload;
    }

    private AiChatModelResponse parseResponse(String responseBody, String fallbackModel) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                throw new ServiceException(CHAT_MODEL_RESPONSE_INVALID, "Chat Model 响应缺少 choices");
            }
            JsonNode choice = choices.get(0);
            JsonNode contentNode = choice.path("message").path("content");
            if (!contentNode.isTextual()) {
                throw new ServiceException(CHAT_MODEL_RESPONSE_INVALID, "Chat Model 响应内容格式非法");
            }
            JsonNode usage = root.path("usage");
            Integer promptTokens = intValueOrNull(usage.path("prompt_tokens"));
            Integer completionTokens = intValueOrNull(usage.path("completion_tokens"));
            Integer totalTokens = intValueOrNull(usage.path("total_tokens"));
            return AiChatModelResponse.builder()
                    .model(root.path("model").asText(fallbackModel))
                    .content(contentNode.asText())
                    .finishReason(choice.path("finish_reason").asText(null))
                    .promptTokens(promptTokens)
                    .completionTokens(completionTokens)
                    .totalTokens(totalTokens)
                    .metadata(buildResponseMetadata(root))
                    .build();
        } catch (JsonProcessingException ex) {
            throw new ServiceException(CHAT_MODEL_RESPONSE_INVALID, "Chat Model 响应解析失败");
        }
    }

    private Map<String, Object> buildResponseMetadata(JsonNode root) {
        Map<String, Object> metadata = new HashMap<>(3);
        if (root.path("id").isTextual()) {
            metadata.put("id", root.path("id").asText());
        }
        if (root.path("object").isTextual()) {
            metadata.put("object", root.path("object").asText());
        }
        if (root.path("created").isNumber()) {
            metadata.put("created", root.path("created").asLong());
        }
        return metadata.isEmpty() ? Collections.emptyMap() : metadata;
    }

    private Integer intValueOrNull(JsonNode node) {
        return node.isInt() || node.isLong() ? node.asInt() : null;
    }

    private String requiredConfig(String value, String fieldName) {
        if (!hasText(value)) {
            log.warn("OpenAI compatible chat config missing, field={}, apiKey={}",
                    fieldName, maskApiKey(modelProperties.getApiKey()));
            throw new ServiceException(CHAT_MODEL_CONFIG_INVALID, "Chat Model 配置不完整");
        }
        return value.trim();
    }

    private Duration toDuration(Integer seconds, int defaultSeconds) {
        int positiveSeconds = seconds == null || seconds <= 0 ? defaultSeconds : seconds;
        return Duration.ofSeconds(positiveSeconds);
    }

    private long elapsedMillis(long startNanos) {
        return Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
    }

    private void logCallException(URI chatUri, String chatModel, int messageCount, long startNanos,
                                  String apiKey, String reason, Exception ex) {
        log.warn("OpenAI compatible chat call exception, endpoint={}, model={}, messageCount={}, reason={}, elapsedMs={}, apiKey={}, errorType={}, error={}",
                sanitizeEndpoint(chatUri), chatModel, messageCount, reason, elapsedMillis(startNanos),
                maskApiKey(apiKey), ex.getClass().getSimpleName(), ex.getMessage());
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
