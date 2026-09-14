package cn.iocoder.yudao.module.ai.service.rag.dify;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.ai.service.chatmodel.AiChatModelResponse;
import cn.iocoder.yudao.module.ai.service.rag.RagChatCitation;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static cn.iocoder.yudao.module.ai.enums.AiRagErrorCodeConstants.*;

/** Calls a server-configured Dify app only for its explicitly bound tenant and knowledge base. */
@Slf4j
@Component
public class DifyRagClient {
    private final DifyProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public DifyRagClient(DifyProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(1, properties.getConnectTimeoutSeconds())))
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    public record Request(Long tenantId, Long knowledgeBaseId, Long userId, Long conversationId,
                          String externalConversationId, String question) { }
    public record Result(String conversationId, AiChatModelResponse modelResponse, List<RagChatCitation> citations) { }

    public boolean isBound(Long tenantId, Long knowledgeBaseId) {
        return tenantId != null && knowledgeBaseId != null
                && Objects.equals(tenantId, properties.getTenantId())
                && Objects.equals(knowledgeBaseId, properties.getKnowledgeBaseId());
    }

    public Result chat(Request request) {
        if (request == null || !isBound(request.tenantId(), request.knowledgeBaseId())) {
            throw new ServiceException(RAG_KNOWLEDGE_ACCESS_DENIED, "当前知识库未绑定 Dify 应用");
        }
        if (request.userId() == null || request.conversationId() == null || !StringUtils.hasText(request.question())) {
            throw new ServiceException(RAG_ENGINE_REQUEST_FAILED, "Dify 请求参数不完整");
        }
        if (!StringUtils.hasText(properties.getApiKey()) || !StringUtils.hasText(properties.getBaseUrl())
                || !StringUtils.hasText(properties.getDatasetId())) {
            throw new ServiceException(RAG_ENGINE_CONFIG_INVALID, "Dify 应用尚未配置");
        }
        try {
            String base = properties.getBaseUrl().replaceAll("/+$", "");
            URI endpoint = URI.create(base + (base.endsWith("/v1") ? "" : "/v1") + "/chat-messages");
            if (!List.of("http", "https").contains(endpoint.getScheme()) || endpoint.getHost() == null
                    || endpoint.getUserInfo() != null) {
                throw new IllegalArgumentException();
            }
            String user = "leman:t" + request.tenantId() + ":k" + request.knowledgeBaseId()
                    + ":u" + request.userId() + ":c" + request.conversationId();
            String body = objectMapper.writeValueAsString(Map.of("inputs", Map.of(), "query", request.question(),
                    "response_mode", "blocking", "user", user, "auto_generate_name", false,
                    "conversation_id", request.externalConversationId() == null ? "" : request.externalConversationId()));
            HttpRequest httpRequest = HttpRequest.newBuilder(endpoint).version(HttpClient.Version.HTTP_1_1)
                    .timeout(Duration.ofSeconds(Math.max(1, properties.getReadTimeoutSeconds())))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Dify request failed, tenantId={}, knowledgeBaseId={}, conversationId={}, status={}",
                        request.tenantId(), request.knowledgeBaseId(), request.conversationId(), response.statusCode());
                throw new ServiceException(RAG_ENGINE_REQUEST_FAILED, "Dify 暂时无法回答，请稍后重试");
            }
            return parse(response.body(), request);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ServiceException(RAG_ENGINE_REQUEST_FAILED, "Dify 请求已中断");
        } catch (IOException ex) {
            log.warn("Dify transport error, tenantId={}, knowledgeBaseId={}, errorType={}",
                    request.tenantId(), request.knowledgeBaseId(), ex.getClass().getSimpleName());
            throw new ServiceException(RAG_ENGINE_REQUEST_FAILED, "Dify 连接失败或响应超时");
        } catch (IllegalArgumentException ex) {
            throw new ServiceException(RAG_ENGINE_CONFIG_INVALID, "Dify 配置或响应格式无效");
        }
    }

    private Result parse(String body, Request request) throws IOException {
        JsonNode root = objectMapper.readTree(body);
        if (root == null || !root.path("answer").isTextual() || root.path("answer").asText().isBlank()
                || !root.path("conversation_id").isTextual() || root.path("conversation_id").asText().isBlank()) {
            throw new ServiceException(RAG_ENGINE_RESPONSE_INVALID, "Dify 未返回有效回答");
        }
        List<RagChatCitation> citations = new ArrayList<>();
        for (JsonNode source : root.path("metadata").path("retriever_resources")) {
            if (!properties.getDatasetId().equals(source.path("dataset_id").asText())) {
                throw new ServiceException(RAG_KNOWLEDGE_ACCESS_DENIED, "Dify 返回了未授权知识库的引用");
            }
            citations.add(RagChatCitation.builder().knowledgeBaseId(request.knowledgeBaseId())
                    .knowledgeBaseName(source.path("dataset_name").asText())
                    .documentTitle(source.path("document_name").asText())
                    .quoteText(source.path("content").asText()).score(source.path("score").asDouble())
                    .chunkNo(source.path("position").asInt()).documentType("DIFY").build());
        }
        JsonNode usage = root.path("metadata").path("usage");
        AiChatModelResponse model = AiChatModelResponse.builder().model("dify")
                .content(root.path("answer").asText()).finishReason("stop")
                .promptTokens(usage.path("prompt_tokens").asInt())
                .completionTokens(usage.path("completion_tokens").asInt())
                .totalTokens(usage.path("total_tokens").asInt()).metadata(Map.of("ragEngine", "dify")).build();
        return new Result(root.path("conversation_id").asText(), model, citations);
    }
}
