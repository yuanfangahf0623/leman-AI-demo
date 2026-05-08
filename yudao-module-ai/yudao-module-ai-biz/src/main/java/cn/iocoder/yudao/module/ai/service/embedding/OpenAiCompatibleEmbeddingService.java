package cn.iocoder.yudao.module.ai.service.embedding;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
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
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.module.ai.enums.AiEmbeddingErrorCodeConstants.EMBEDDING_CONFIG_INVALID;
import static cn.iocoder.yudao.module.ai.enums.AiEmbeddingErrorCodeConstants.EMBEDDING_REQUEST_FAILED;
import static cn.iocoder.yudao.module.ai.enums.AiEmbeddingErrorCodeConstants.EMBEDDING_RESPONSE_INVALID;

/**
 * OpenAI 兼容 Embedding 服务实现。
 *
 * <p>API Key 和 Base URL 通过 {@link AiProperties} 注入，启动阶段不校验配置，实际调用时再校验。</p>
 */
@Slf4j
public class OpenAiCompatibleEmbeddingService implements AiEmbeddingService {

    private static final String EMBEDDINGS_PATH = "/embeddings";
    private static final int DEFAULT_CONNECT_TIMEOUT_SECONDS = 10;
    private static final int DEFAULT_READ_TIMEOUT_SECONDS = 60;
    private static final int HTTP_SUCCESS_MIN = 200;
    private static final int HTTP_SUCCESS_MAX = 299;

    private final AiProperties.ModelProperties modelProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public OpenAiCompatibleEmbeddingService(AiProperties aiProperties) {
        this(aiProperties, new ObjectMapper());
    }

    public OpenAiCompatibleEmbeddingService(AiProperties aiProperties, ObjectMapper objectMapper) {
        this.modelProperties = aiProperties.getModel() == null ? new AiProperties.ModelProperties() : aiProperties.getModel();
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(toDuration(modelProperties.getConnectTimeoutSeconds(), DEFAULT_CONNECT_TIMEOUT_SECONDS))
                .build();
    }

    @Override
    public List<Double> embed(String text) {
        List<List<Double>> vectors = embedBatch(Collections.singletonList(text));
        return vectors.isEmpty() ? Collections.emptyList() : vectors.get(0);
    }

    @Override
    public List<List<Double>> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return Collections.emptyList();
        }
        return requestEmbeddings(normalizeInputs(texts));
    }

    public String getEmbeddingModel() {
        return modelProperties.getEmbeddingModel();
    }

    private List<List<Double>> requestEmbeddings(List<String> texts) {
        long startNanos = System.nanoTime();
        URI embeddingsUri = buildEmbeddingsUri();
        String apiKey = requiredConfig(modelProperties.getApiKey(), "api-key");
        String embeddingModel = requiredConfig(modelProperties.getEmbeddingModel(), "embedding-model");
        String requestBody = buildRequestBody(texts, embeddingModel);
        HttpRequest request = HttpRequest.newBuilder(embeddingsUri)
                .timeout(toDuration(modelProperties.getReadTimeoutSeconds(), DEFAULT_READ_TIMEOUT_SECONDS))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            long elapsedMs = elapsedMillis(startNanos);
            if (response.statusCode() < HTTP_SUCCESS_MIN || response.statusCode() > HTTP_SUCCESS_MAX) {
                log.warn("OpenAI compatible embedding call failed, endpoint={}, model={}, batchSize={}, status={}, elapsedMs={}, apiKey={}",
                        sanitizeEndpoint(embeddingsUri), embeddingModel, texts.size(), response.statusCode(), elapsedMs, maskApiKey(apiKey));
                throw new ServiceException(EMBEDDING_REQUEST_FAILED, "Embedding 外部调用失败");
            }
            List<List<Double>> embeddings;
            try {
                embeddings = parseEmbeddings(response.body(), texts.size());
            } catch (ServiceException ex) {
                log.warn("OpenAI compatible embedding response invalid, endpoint={}, model={}, batchSize={}, elapsedMs={}, apiKey={}, code={}",
                        sanitizeEndpoint(embeddingsUri), embeddingModel, texts.size(), elapsedMs, maskApiKey(apiKey), ex.getCode());
                throw ex;
            }
            log.info("OpenAI compatible embedding call success, endpoint={}, model={}, batchSize={}, elapsedMs={}",
                    sanitizeEndpoint(embeddingsUri), embeddingModel, texts.size(), elapsedMs);
            return embeddings;
        } catch (HttpTimeoutException ex) {
            logCallException(embeddingsUri, embeddingModel, texts.size(), startNanos, apiKey, "timeout", ex);
            throw new ServiceException(EMBEDDING_REQUEST_FAILED, "Embedding 外部调用超时");
        } catch (IOException ex) {
            logCallException(embeddingsUri, embeddingModel, texts.size(), startNanos, apiKey, "io", ex);
            throw new ServiceException(EMBEDDING_REQUEST_FAILED, "Embedding 外部调用失败");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logCallException(embeddingsUri, embeddingModel, texts.size(), startNanos, apiKey, "interrupted", ex);
            throw new ServiceException(EMBEDDING_REQUEST_FAILED, "Embedding 外部调用被中断");
        }
    }

    private URI buildEmbeddingsUri() {
        String baseUrl = requiredConfig(modelProperties.getBaseUrl(), "base-url");
        String normalizedBaseUrl = trimTrailingSlash(baseUrl.trim());
        String url = normalizedBaseUrl.endsWith(EMBEDDINGS_PATH) ? normalizedBaseUrl : normalizedBaseUrl + EMBEDDINGS_PATH;
        try {
            URI uri = URI.create(url);
            if (uri.getScheme() == null || uri.getHost() == null) {
                throw new IllegalArgumentException("base-url must include scheme and host");
            }
            return uri;
        } catch (IllegalArgumentException ex) {
            log.warn("OpenAI compatible embedding config invalid, field=base-url, errorType={}, error={}",
                    ex.getClass().getSimpleName(), ex.getMessage());
            throw new ServiceException(EMBEDDING_CONFIG_INVALID, "Embedding 服务地址配置非法");
        }
    }

    private String buildRequestBody(List<String> texts, String embeddingModel) {
        Map<String, Object> request = new HashMap<>(2);
        request.put("model", embeddingModel);
        request.put("input", texts.size() == 1 ? texts.get(0) : texts);
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException ex) {
            throw new ServiceException(EMBEDDING_REQUEST_FAILED, "Embedding 请求构造失败");
        }
    }

    private List<List<Double>> parseEmbeddings(String responseBody, int expectedSize) {
        try {
            JsonNode dataNode = objectMapper.readTree(responseBody).path("data");
            if (!dataNode.isArray()) {
                throw new ServiceException(EMBEDDING_RESPONSE_INVALID, "Embedding 响应格式非法");
            }
            List<List<Double>> embeddings = new ArrayList<>(Collections.nCopies(expectedSize, null));
            int fallbackIndex = 0;
            for (JsonNode itemNode : dataNode) {
                int index = itemNode.path("index").isInt() ? itemNode.path("index").asInt() : fallbackIndex;
                if (index < 0 || index >= expectedSize) {
                    throw new ServiceException(EMBEDDING_RESPONSE_INVALID, "Embedding 响应索引非法");
                }
                embeddings.set(index, parseVector(itemNode.path("embedding")));
                fallbackIndex++;
            }
            if (embeddings.stream().anyMatch(vector -> vector == null || vector.isEmpty())) {
                throw new ServiceException(EMBEDDING_RESPONSE_INVALID, "Embedding 响应向量缺失");
            }
            return embeddings;
        } catch (JsonProcessingException ex) {
            throw new ServiceException(EMBEDDING_RESPONSE_INVALID, "Embedding 响应解析失败");
        }
    }

    private List<Double> parseVector(JsonNode embeddingNode) {
        if (!embeddingNode.isArray()) {
            throw new ServiceException(EMBEDDING_RESPONSE_INVALID, "Embedding 响应向量格式非法");
        }
        List<Double> vector = new ArrayList<>(embeddingNode.size());
        for (JsonNode valueNode : embeddingNode) {
            if (!valueNode.isNumber()) {
                throw new ServiceException(EMBEDDING_RESPONSE_INVALID, "Embedding 响应向量值非法");
            }
            vector.add(valueNode.asDouble());
        }
        return vector;
    }

    private List<String> normalizeInputs(List<String> texts) {
        List<String> inputs = new ArrayList<>(texts.size());
        for (String text : texts) {
            inputs.add(text == null ? "" : text);
        }
        return inputs;
    }

    private String requiredConfig(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            log.warn("OpenAI compatible embedding config missing, field={}, apiKey={}",
                    fieldName, maskApiKey(modelProperties.getApiKey()));
            throw new ServiceException(EMBEDDING_CONFIG_INVALID, "Embedding 配置不完整");
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

    private void logCallException(URI embeddingsUri, String embeddingModel, int batchSize, long startNanos,
                                  String apiKey, String reason, Exception ex) {
        log.warn("OpenAI compatible embedding call exception, endpoint={}, model={}, batchSize={}, reason={}, elapsedMs={}, apiKey={}, errorType={}, error={}",
                sanitizeEndpoint(embeddingsUri), embeddingModel, batchSize, reason, elapsedMillis(startNanos),
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

}
