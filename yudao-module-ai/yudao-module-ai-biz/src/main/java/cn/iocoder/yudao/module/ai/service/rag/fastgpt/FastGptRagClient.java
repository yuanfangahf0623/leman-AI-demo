package cn.iocoder.yudao.module.ai.service.rag.fastgpt;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.ai.framework.config.AiProperties;
import cn.iocoder.yudao.module.ai.service.chatmodel.AiChatModelMessage;
import cn.iocoder.yudao.module.ai.service.chatmodel.AiChatModelResponse;
import cn.iocoder.yudao.module.ai.service.rag.RagChatCitation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static cn.iocoder.yudao.module.ai.enums.AiRagErrorCodeConstants.RAG_ENGINE_CONFIG_INVALID;
import static cn.iocoder.yudao.module.ai.enums.AiRagErrorCodeConstants.RAG_ENGINE_REQUEST_FAILED;
import static cn.iocoder.yudao.module.ai.enums.AiRagErrorCodeConstants.RAG_ENGINE_RESPONSE_INVALID;

/**
 * FastGPT OpenAI-compatible chat client used as an external RAG engine.
 */
@Slf4j
@Component
public class FastGptRagClient {

    private static final String API_V1_CHAT_COMPLETIONS_PATH = "/api/v1/chat/completions";
    private static final String V1_CHAT_COMPLETIONS_PATH = "/v1/chat/completions";
    private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";
    private static final int DEFAULT_CONNECT_TIMEOUT_SECONDS = 10;
    private static final int DEFAULT_READ_TIMEOUT_SECONDS = 120;
    private static final int HTTP_SUCCESS_MIN = 200;
    private static final int HTTP_SUCCESS_MAX = 299;
    private static final int MAX_CITATION_SCAN_DEPTH = 6;
    private static final int MAX_DEBUG_TEXT_LENGTH = 500;
    private static final Pattern MARKDOWN_LINK_PATTERN = Pattern.compile("!?\\[[^\\]]*]\\((https?://[^\\s)]+)\\)");
    private static final Pattern RAW_URL_PATTERN = Pattern.compile("https?://[^\\s)\\]]+");

    private final AiProperties.FastGptProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public FastGptRagClient(AiProperties aiProperties, ObjectMapper objectMapper) {
        this.properties = aiProperties.getFastgpt() == null
                ? new AiProperties.FastGptProperties() : aiProperties.getFastgpt();
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(toDuration(properties.getConnectTimeoutSeconds(), DEFAULT_CONNECT_TIMEOUT_SECONDS))
                .build();
    }

    public FastGptRagResult chat(FastGptRagRequest request) {
        validateRequest(request);
        URI chatUri = buildChatUri();
        String apiKey = requiredConfig(properties.getApiKey(), "api-key");
        String model = resolveModel();
        String requestBody = buildRequestBody(request, model);
        HttpRequest httpRequest = HttpRequest.newBuilder(chatUri)
                // FastGPT is served by a Node.js stack in local Docker. Java HttpClient may try h2c upgrade
                // on plain HTTP and some deployments close the socket before sending headers, so pin HTTP/1.1.
                .version(HttpClient.Version.HTTP_1_1)
                .timeout(toDuration(properties.getReadTimeoutSeconds(), DEFAULT_READ_TIMEOUT_SECONDS))
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
                log.warn("FastGPT RAG call failed, endpoint={}, model={}, tenantId={}, knowledgeBaseId={}, conversationId={}, status={}, elapsedMs={}, apiKey={}",
                        sanitizeEndpoint(chatUri), model, request.getTenantId(), request.getKnowledgeBaseId(),
                        request.getConversationId(), response.statusCode(), elapsedMs, maskApiKey(apiKey));
                throw new ServiceException(RAG_ENGINE_REQUEST_FAILED, "FastGPT RAG request failed");
            }
            FastGptRagResult result = parseResponse(response.body(), model, request, chatUri, elapsedMs);
            AiChatModelResponse modelResponse = result.getModelResponse();
            log.info("FastGPT RAG call success, endpoint={}, model={}, tenantId={}, knowledgeBaseId={}, conversationId={}, citationCount={}, promptTokens={}, completionTokens={}, totalTokens={}, elapsedMs={}",
                    sanitizeEndpoint(chatUri), modelResponse.getModel(), request.getTenantId(), request.getKnowledgeBaseId(),
                    request.getConversationId(), result.getCitations().size(), modelResponse.getPromptTokens(),
                    modelResponse.getCompletionTokens(), modelResponse.getTotalTokens(), elapsedMs);
            return result;
        } catch (HttpTimeoutException ex) {
            logCallException(chatUri, model, request, startNanos, apiKey, "timeout", ex);
            throw new ServiceException(RAG_ENGINE_REQUEST_FAILED, "FastGPT RAG request timeout");
        } catch (IOException ex) {
            logCallException(chatUri, model, request, startNanos, apiKey, "io", ex);
            throw new ServiceException(RAG_ENGINE_REQUEST_FAILED, "FastGPT RAG request failed");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logCallException(chatUri, model, request, startNanos, apiKey, "interrupted", ex);
            throw new ServiceException(RAG_ENGINE_REQUEST_FAILED, "FastGPT RAG request interrupted");
        }
    }

    private void validateRequest(FastGptRagRequest request) {
        if (request == null || request.getTenantId() == null || request.getKnowledgeBaseId() == null
                || request.getConversationId() == null || request.getQuestion() == null || request.getQuestion().isBlank()
                || request.getMessages() == null || request.getMessages().isEmpty()) {
            throw new ServiceException(RAG_ENGINE_REQUEST_FAILED, "FastGPT RAG request is incomplete");
        }
    }

    private URI buildChatUri() {
        String baseUrl = requiredConfig(properties.getBaseUrl(), "base-url");
        String normalizedBaseUrl = trimTrailingSlash(baseUrl.trim());
        String url;
        if (normalizedBaseUrl.endsWith(API_V1_CHAT_COMPLETIONS_PATH)
                || normalizedBaseUrl.endsWith(V1_CHAT_COMPLETIONS_PATH)
                || normalizedBaseUrl.endsWith(CHAT_COMPLETIONS_PATH)) {
            url = normalizedBaseUrl;
        } else if (normalizedBaseUrl.endsWith("/api/v1") || normalizedBaseUrl.endsWith("/v1")) {
            url = normalizedBaseUrl + CHAT_COMPLETIONS_PATH;
        } else if (normalizedBaseUrl.endsWith("/api")) {
            url = normalizedBaseUrl + V1_CHAT_COMPLETIONS_PATH;
        } else {
            url = normalizedBaseUrl + API_V1_CHAT_COMPLETIONS_PATH;
        }
        try {
            URI uri = URI.create(url);
            if (uri.getScheme() == null || uri.getHost() == null) {
                throw new IllegalArgumentException("base-url must include scheme and host");
            }
            return uri;
        } catch (IllegalArgumentException ex) {
            log.warn("FastGPT RAG config invalid, field=base-url, errorType={}, error={}",
                    ex.getClass().getSimpleName(), ex.getMessage());
            throw new ServiceException(RAG_ENGINE_CONFIG_INVALID, "FastGPT RAG base-url is invalid");
        }
    }

    private String buildRequestBody(FastGptRagRequest request, String model) {
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", model);
        requestBody.put("chatId", buildChatId(request));
        requestBody.put("stream", false);
        requestBody.put("detail", true);
        if (request.getUserId() != null) {
            requestBody.put("customUid", String.valueOf(request.getUserId()));
        }
        requestBody.put("messages", toMessagePayload(request.getMessages()));
        try {
            return objectMapper.writeValueAsString(requestBody);
        } catch (JsonProcessingException ex) {
            throw new ServiceException(RAG_ENGINE_REQUEST_FAILED, "FastGPT RAG request build failed");
        }
    }

    private String buildChatId(FastGptRagRequest request) {
        return "tenant-" + request.getTenantId() + "-conversation-" + request.getConversationId();
    }

    private List<Map<String, String>> toMessagePayload(List<AiChatModelMessage> messages) {
        List<Map<String, String>> payload = new ArrayList<>(messages.size());
        for (AiChatModelMessage message : messages) {
            if (message == null || !hasText(message.getRole())) {
                continue;
            }
            Map<String, String> item = new LinkedHashMap<>(2);
            item.put("role", message.getRole().trim());
            item.put("content", message.getContent() == null ? "" : message.getContent());
            payload.add(item);
        }
        if (payload.isEmpty()) {
            throw new ServiceException(RAG_ENGINE_REQUEST_FAILED, "FastGPT RAG messages are empty");
        }
        return payload;
    }

    private FastGptRagResult parseResponse(String responseBody, String fallbackModel, FastGptRagRequest request,
                                           URI chatUri, long elapsedMs) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                throw new ServiceException(RAG_ENGINE_RESPONSE_INVALID, "FastGPT RAG response missing choices");
            }
            JsonNode choice = choices.get(0);
            JsonNode contentNode = choice.path("message").path("content");
            if (!contentNode.isTextual()) {
                throw new ServiceException(RAG_ENGINE_RESPONSE_INVALID, "FastGPT RAG response content is invalid");
            }
            String content = contentNode.asText();
            JsonNode usage = root.path("usage");
            AiChatModelResponse modelResponse = AiChatModelResponse.builder()
                    .model(root.path("model").asText(fallbackModel))
                    .content(content)
                    .finishReason(choice.path("finish_reason").asText(null))
                    .promptTokens(intValueOrNull(usage.path("prompt_tokens")))
                    .completionTokens(intValueOrNull(usage.path("completion_tokens")))
                    .totalTokens(intValueOrNull(usage.path("total_tokens")))
                    .metadata(buildResponseMetadata(root))
                    .build();
            CitationParseResult citationParseResult = parseCitationResult(root);
            List<RagChatCitation> citations = citationParseResult.citations();
            String citationSource = citationParseResult.source();
            if (citations.isEmpty()) {
                citations = parseAnswerLinks(content);
                citationSource = citations.isEmpty() ? "none" : "answer-link-fallback";
            }
            return FastGptRagResult.builder()
                    .modelResponse(modelResponse)
                    .citations(citations)
                    .debugInfo(buildDebugInfo(root, request, chatUri, elapsedMs, citations.size(), citationSource,
                            citationParseResult.scannedArrayCount(), citationParseResult.parsedCitationCount()))
                    .build();
        } catch (JsonProcessingException ex) {
            throw new ServiceException(RAG_ENGINE_RESPONSE_INVALID, "FastGPT RAG response parse failed");
        }
    }

    private Map<String, Object> buildResponseMetadata(JsonNode root) {
        Map<String, Object> metadata = new HashMap<>(4);
        if (root.path("id").isTextual()) {
            metadata.put("id", root.path("id").asText());
        }
        if (root.path("object").isTextual()) {
            metadata.put("object", root.path("object").asText());
        }
        if (root.path("created").isNumber()) {
            metadata.put("created", root.path("created").asLong());
        }
        if (hasText(properties.getAppId())) {
            metadata.put("appId", properties.getAppId().trim());
        }
        return metadata.isEmpty() ? Collections.emptyMap() : metadata;
    }

    private CitationParseResult parseCitationResult(JsonNode root) {
        List<JsonNode> citationArrays = new ArrayList<>();
        collectCitationArrays(root, citationArrays, 0);
        if (citationArrays.isEmpty()) {
            return new CitationParseResult(Collections.emptyList(), "none", 0, 0);
        }
        Map<String, RagChatCitation> citations = new LinkedHashMap<>();
        for (JsonNode citationArray : citationArrays) {
            for (JsonNode item : citationArray) {
                RagChatCitation citation = toCitation(item);
                if (citation != null) {
                    citations.putIfAbsent(citationKey(citation), citation);
                }
            }
        }
        return new CitationParseResult(new ArrayList<>(citations.values()), "fastgpt-quote-list",
                citationArrays.size(), citations.size());
    }

    private void collectCitationArrays(JsonNode node, List<JsonNode> citationArrays, int depth) {
        if (node == null || node.isMissingNode() || node.isNull() || depth > MAX_CITATION_SCAN_DEPTH) {
            return;
        }
        if (node.isArray()) {
            if (looksLikeCitationArray(node)) {
                citationArrays.add(node);
            }
            for (JsonNode item : node) {
                collectCitationArrays(item, citationArrays, depth + 1);
            }
            return;
        }
        if (!node.isObject()) {
            return;
        }
        node.fields().forEachRemaining(entry -> {
            String fieldName = entry.getKey().toLowerCase(Locale.ROOT);
            JsonNode child = entry.getValue();
            if (fieldName.contains("quote") || fieldName.contains("citation") || fieldName.contains("reference")
                    || fieldName.contains("source") || fieldName.contains("dataset")) {
                collectCitationArrays(child, citationArrays, depth + 1);
            } else if (child.isObject() || child.isArray()) {
                collectCitationArrays(child, citationArrays, depth + 1);
            }
        });
    }

    private boolean looksLikeCitationArray(JsonNode node) {
        if (!node.isArray() || node.isEmpty()) {
            return false;
        }
        JsonNode firstObject = null;
        for (JsonNode item : node) {
            if (item.isObject()) {
                firstObject = item;
                break;
            }
        }
        if (firstObject == null) {
            return false;
        }
        return firstText(firstObject, "content", "text", "quote", "q", "a", "documentTitle", "title",
                "sourceName", "fileName", "filename", "datasetName", "collectionName", "knowledgeBaseName") != null;
    }

    private RagChatCitation toCitation(JsonNode item) {
        if (item == null || !item.isObject()) {
            return null;
        }
        String quoteText = firstText(item, "content", "text", "quote", "q", "a", "pageContent", "chunkContent",
                "sourceContent");
        String documentTitle = firstText(item, "documentTitle", "title", "sourceName", "fileName", "filename",
                "sourceTitle", "name", "datasetName", "collectionName");
        String knowledgeBaseName = firstKnowledgeBaseName(item);
        if (!hasText(quoteText) && !hasText(documentTitle)) {
            return null;
        }
        return RagChatCitation.builder()
                .knowledgeBaseId(longValueOrNull(item, "knowledgeBaseId", "datasetId", "collectionId"))
                .knowledgeBaseName(knowledgeBaseName)
                .documentId(longValueOrNull(item, "documentId", "fileId", "sourceId"))
                .chunkId(longValueOrNull(item, "chunkId", "qId", "id"))
                .chunkNo(intValueOrNull(item, "chunkNo", "chunkIndex", "index"))
                .documentTitle(documentTitle)
                .score(doubleValueOrNull(item, "score", "similarity", "similarityScore"))
                .quoteText(quoteText)
                .meetingId(longValueOrNull(item, "meetingId", "meeting_id"))
                .documentType(firstText(item, "documentType", "document_type"))
                .projectCode(firstText(item, "projectCode", "project_code"))
                .build();
    }

    private String firstText(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode value = node.path(fieldName);
            if (value.isTextual() && hasText(value.asText())) {
                return value.asText();
            }
            JsonNode metadataValue = node.path("metadata").path(fieldName);
            if (metadataValue.isTextual() && hasText(metadataValue.asText())) {
                return metadataValue.asText();
            }
            JsonNode sourceValue = node.path("source").path(fieldName);
            if (sourceValue.isTextual() && hasText(sourceValue.asText())) {
                return sourceValue.asText();
            }
            JsonNode documentValue = node.path("document").path(fieldName);
            if (documentValue.isTextual() && hasText(documentValue.asText())) {
                return documentValue.asText();
            }
            JsonNode datasetValue = node.path("dataset").path(fieldName);
            if (datasetValue.isTextual() && hasText(datasetValue.asText())) {
                return datasetValue.asText();
            }
            JsonNode collectionValue = node.path("collection").path(fieldName);
            if (collectionValue.isTextual() && hasText(collectionValue.asText())) {
                return collectionValue.asText();
            }
            JsonNode knowledgeBaseValue = node.path("knowledgeBase").path(fieldName);
            if (knowledgeBaseValue.isTextual() && hasText(knowledgeBaseValue.asText())) {
                return knowledgeBaseValue.asText();
            }
        }
        return null;
    }

    private String firstKnowledgeBaseName(JsonNode node) {
        String value = firstText(node, "knowledgeBaseName", "datasetName", "datasetTitle", "collectionName",
                "collectionTitle", "kbName", "kbTitle", "databaseName");
        if (hasText(value)) {
            return value;
        }
        return firstNestedText(node, new String[]{"dataset", "collection", "knowledgeBase", "kb", "database"},
                "name", "title");
    }

    private String firstNestedText(JsonNode node, String[] parentNames, String... fieldNames) {
        for (String parentName : parentNames) {
            JsonNode parent = node.path(parentName);
            if (!parent.isObject()) {
                continue;
            }
            for (String fieldName : fieldNames) {
                JsonNode value = parent.path(fieldName);
                if (value.isTextual() && hasText(value.asText())) {
                    return value.asText();
                }
            }
        }
        return null;
    }

    private List<RagChatCitation> parseAnswerLinks(String content) {
        if (!hasText(content)) {
            return Collections.emptyList();
        }
        Set<String> urls = new LinkedHashSet<>();
        Matcher markdownMatcher = MARKDOWN_LINK_PATTERN.matcher(content);
        while (markdownMatcher.find()) {
            urls.add(cleanUrl(markdownMatcher.group(1)));
        }
        Matcher rawMatcher = RAW_URL_PATTERN.matcher(content);
        while (rawMatcher.find()) {
            urls.add(cleanUrl(rawMatcher.group()));
        }
        if (urls.isEmpty()) {
            return Collections.emptyList();
        }
        List<RagChatCitation> citations = new ArrayList<>(urls.size());
        for (String url : urls) {
            if (!hasText(url)) {
                continue;
            }
            citations.add(RagChatCitation.builder()
                    .documentTitle(extractUrlTitle(url))
                    .quoteText(url)
                    .build());
        }
        return citations;
    }

    private String cleanUrl(String url) {
        if (url == null) {
            return "";
        }
        String cleaned = url.trim();
        while (cleaned.endsWith(".") || cleaned.endsWith(",") || cleaned.endsWith(";") || cleaned.endsWith(")")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }
        return cleaned;
    }

    private String extractUrlTitle(String url) {
        String filename = queryParam(url, "filename");
        if (hasText(filename)) {
            return filename;
        }
        try {
            URI uri = URI.create(url);
            String path = uri.getPath();
            if (hasText(path)) {
                String lastSegment = path.substring(path.lastIndexOf('/') + 1);
                if (hasText(lastSegment)) {
                    return decodeUrl(lastSegment);
                }
            }
        } catch (IllegalArgumentException ignored) {
            // Keep the generic title for malformed model-generated links.
        }
        return "FastGPT 来源链接";
    }

    private String queryParam(String url, String paramName) {
        int queryStart = url.indexOf('?');
        if (queryStart < 0 || queryStart + 1 >= url.length()) {
            return null;
        }
        String query = url.substring(queryStart + 1);
        for (String item : query.split("&")) {
            int separator = item.indexOf('=');
            if (separator <= 0) {
                continue;
            }
            String name = decodeUrl(item.substring(0, separator));
            if (paramName.equals(name)) {
                return decodeUrl(item.substring(separator + 1));
            }
        }
        return null;
    }

    private String decodeUrl(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return value;
        }
    }

    private String citationKey(RagChatCitation citation) {
        return String.valueOf(citation.getDocumentTitle()) + "|"
                + citation.getChunkId() + "|"
                + citation.getChunkNo() + "|"
                + citation.getQuoteText();
    }

    private String buildDebugInfo(JsonNode root, FastGptRagRequest request, URI chatUri, long elapsedMs,
                                  int citationCount, String citationSource, int scannedCitationArrayCount,
                                  int parsedCitationCount) {
        StringBuilder debug = new StringBuilder();
        debug.append("## FastGPT 平台层调试轨迹\n");
        debug.append("> 说明：以下为本系统记录的请求、工作流节点、引用解析和耗时信息，不包含模型内部原始思考过程。\n\n");
        debug.append("### 1. 请求侧\n");
        debug.append("- endpoint=").append(sanitizeEndpoint(chatUri)).append('\n');
        debug.append("- model=").append(root.path("model").asText(resolveModel())).append('\n');
        debug.append("- chatId=").append(buildChatId(request)).append('\n');
        debug.append("- tenantId=").append(request.getTenantId())
                .append(", departmentId=").append(request.getDepartmentId())
                .append(", knowledgeBaseId=").append(request.getKnowledgeBaseId())
                .append(", conversationId=").append(request.getConversationId()).append('\n');
        debug.append("- detail=true, stream=false, messageCount=")
                .append(request.getMessages() == null ? 0 : request.getMessages().size())
                .append(", questionChars=").append(request.getQuestion() == null ? 0 : request.getQuestion().length())
                .append('\n');
        debug.append("- apiKey=已配置但不展示\n\n");

        debug.append("### 2. 响应侧\n");
        debug.append("- elapsedMs=").append(elapsedMs).append('\n');
        debug.append("- citationCount=").append(citationCount)
                .append(", citationSource=").append(citationSource)
                .append(", scannedCitationArrayCount=").append(scannedCitationArrayCount)
                .append(", parsedCitationCount=").append(parsedCitationCount).append('\n');
        JsonNode usage = root.path("usage");
        if (usage.isObject()) {
            debug.append("- usage: promptTokens=").append(intValueOrNull(usage.path("prompt_tokens")))
                    .append(", completionTokens=").append(intValueOrNull(usage.path("completion_tokens")))
                    .append(", totalTokens=").append(intValueOrNull(usage.path("total_tokens"))).append('\n');
        }
        JsonNode choices = root.path("choices");
        if (choices.isArray() && !choices.isEmpty()) {
            debug.append("- finishReason=").append(choices.get(0).path("finish_reason").asText("")).append('\n');
        }
        debug.append('\n');
        JsonNode responseData = root.path("responseData");
        if (!responseData.isArray() || responseData.isEmpty()) {
            debug.append("### 3. FastGPT 工作流节点\n");
            debug.append("- responseData 为空：FastGPT 未返回节点级调试数据。\n\n");
            appendCitationDebug(debug, citationSource, citationCount);
            return debug.toString();
        }
        debug.append("### 3. FastGPT 工作流节点\n");
        debug.append("- workflowNodes=").append(responseData.size()).append('\n');
        for (int i = 0; i < responseData.size(); i++) {
            JsonNode node = responseData.get(i);
            appendNodeDebug(debug, i + 1, node);
        }
        debug.append('\n');
        appendCitationDebug(debug, citationSource, citationCount);
        return debug.toString();
    }

    private void appendCitationDebug(StringBuilder debug, String citationSource, int citationCount) {
        debug.append("### 4. 引用解析\n");
        debug.append("- 解析策略：优先解析 FastGPT responseData 中的 quoteList/reference/citation/source/dataset 数组；")
                .append("如果没有结构化引用，再从回答正文里的文件链接兜底生成引用。\n");
        debug.append("- citationSource=").append(citationSource).append('\n');
        if ("none".equals(citationSource)) {
            debug.append("- 结果：没有结构化引用，也没有可识别的回答链接，前端会显示 0 条引用。\n");
        } else if ("answer-link-fallback".equals(citationSource)) {
            debug.append("- 结果：FastGPT 未返回结构化 quoteList，本系统从回答正文中的链接兜底生成引用。\n");
        } else {
            debug.append("- 结果：从 FastGPT 结构化引用中解析出 ").append(citationCount).append(" 条引用。\n");
        }
    }

    private void appendNodeDebug(StringBuilder debug, int index, JsonNode node) {
        String moduleName = textValue(node, "moduleName");
        String moduleType = textValue(node, "moduleType");
        debug.append(index).append(". ").append(defaultText(moduleName, "未知节点"))
                .append(" [").append(defaultText(moduleType, "-")).append("]");
        if (node.path("runningTime").isNumber()) {
            debug.append(", runningTime=").append(node.path("runningTime").asLong()).append("ms");
        }
        debug.append('\n');
        if ("datasetSearchNode".equals(moduleType)) {
            debug.append("   query=").append(truncateDebug(textValue(node, "query"))).append('\n')
                    .append("   searchMode=").append(textValue(node, "searchMode"))
                    .append(", embeddingModel=").append(textValue(node, "embeddingModel"))
                    .append(", limit=").append(intValueOrNull(node.path("limit")))
                    .append(", similarity=").append(doubleValueOrNull(node, "similarity"))
                    .append(", totalPoints=").append(intValueOrNull(node.path("totalPoints")))
                    .append(", quoteListCount=").append(arraySize(node.path("quoteList"))).append('\n');
        } else if ("chatNode".equals(moduleType)) {
            debug.append("   model=").append(textValue(node, "model"))
                    .append(", inputTokens=").append(intValueOrNull(node.path("inputTokens")))
                    .append(", outputTokens=").append(intValueOrNull(node.path("outputTokens")))
                    .append(", totalPoints=").append(intValueOrNull(node.path("totalPoints")))
                    .append(", finishReason=").append(textValue(node, "finishReason")).append('\n');
            String reasoningText = textValue(node, "reasoningText");
            if (hasText(reasoningText)) {
                debug.append("   reasoningTextLength=").append(reasoningText.length())
                        .append("（仅记录长度，不展示完整内部推理）\n");
            }
        }
    }

    private String textValue(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        if (value.isTextual()) {
            return value.asText();
        }
        if (value.isNumber() || value.isBoolean()) {
            return value.asText();
        }
        return null;
    }

    private String defaultText(String value, String defaultValue) {
        return hasText(value) ? value : defaultValue;
    }

    private String truncateDebug(String value) {
        if (!hasText(value)) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() <= MAX_DEBUG_TEXT_LENGTH) {
            return trimmed;
        }
        return trimmed.substring(0, MAX_DEBUG_TEXT_LENGTH) + "...";
    }

    private int arraySize(JsonNode node) {
        return node != null && node.isArray() ? node.size() : 0;
    }

    private Long longValueOrNull(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode value = nestedValue(node, fieldName);
            if (value.isIntegralNumber()) {
                return value.asLong();
            }
            if (value.isTextual()) {
                try {
                    return Long.valueOf(value.asText());
                } catch (NumberFormatException ignored) {
                    // Ignore non-numeric external ids.
                }
            }
        }
        return null;
    }

    private JsonNode nestedValue(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        if (!value.isMissingNode() && !value.isNull()) {
            return value;
        }
        String[] parents = {"metadata", "source", "document", "dataset", "collection", "knowledgeBase"};
        for (String parent : parents) {
            value = node.path(parent).path(fieldName);
            if (!value.isMissingNode() && !value.isNull()) {
                return value;
            }
        }
        return node.path(fieldName);
    }

    private Integer intValueOrNull(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode value = node.path(fieldName);
            if (value.isInt() || value.isLong()) {
                return value.asInt();
            }
            if (value.isTextual()) {
                try {
                    return Integer.valueOf(value.asText());
                } catch (NumberFormatException ignored) {
                    // Ignore non-numeric external ids.
                }
            }
        }
        return null;
    }

    private Integer intValueOrNull(JsonNode node) {
        return node.isInt() || node.isLong() ? node.asInt() : null;
    }

    private Double doubleValueOrNull(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode value = node.path(fieldName);
            if (value.isNumber()) {
                return value.asDouble();
            }
            if (value.isTextual()) {
                try {
                    return Double.valueOf(value.asText());
                } catch (NumberFormatException ignored) {
                    // Ignore non-numeric scores.
                }
            }
        }
        return null;
    }

    private String resolveModel() {
        return hasText(properties.getModel()) ? properties.getModel().trim() : "fastgpt";
    }

    private String requiredConfig(String value, String fieldName) {
        if (!hasText(value)) {
            log.warn("FastGPT RAG config missing, field={}, apiKey={}", fieldName, maskApiKey(properties.getApiKey()));
            throw new ServiceException(RAG_ENGINE_CONFIG_INVALID, "FastGPT RAG configuration is incomplete");
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

    private void logCallException(URI chatUri, String model, FastGptRagRequest request, long startNanos,
                                  String apiKey, String reason, Exception ex) {
        log.warn("FastGPT RAG call exception, endpoint={}, model={}, tenantId={}, knowledgeBaseId={}, conversationId={}, reason={}, elapsedMs={}, apiKey={}, errorType={}, error={}",
                sanitizeEndpoint(chatUri), model, request.getTenantId(), request.getKnowledgeBaseId(),
                request.getConversationId(), reason, elapsedMillis(startNanos), maskApiKey(apiKey),
                ex.getClass().getSimpleName(), ex.getMessage());
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

    private record CitationParseResult(List<RagChatCitation> citations, String source, int scannedArrayCount,
                                       int parsedCitationCount) {
    }
}
