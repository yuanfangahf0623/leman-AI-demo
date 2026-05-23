package cn.iocoder.yudao.module.ai.service.rag.fastgpt;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.ai.enums.ChatMessageRoleEnum;
import cn.iocoder.yudao.module.ai.framework.config.AiProperties;
import cn.iocoder.yudao.module.ai.service.chatmodel.AiChatModelMessage;
import cn.iocoder.yudao.module.ai.service.rag.RagChatCitation;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static cn.iocoder.yudao.module.ai.enums.AiRagErrorCodeConstants.RAG_ENGINE_CONFIG_INVALID;
import static cn.iocoder.yudao.module.ai.enums.AiRagErrorCodeConstants.RAG_ENGINE_REQUEST_FAILED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FastGptRagClientTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String API_KEY_PLACEHOLDER = "${FASTGPT_API_KEY}";

    @Test
    void chatShouldCallFastGptOpenAiCompatibleEndpoint() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        HttpServer server = startServer(200, """
                {"id":"fastgpt-test","object":"chat.completion","created":1710000000,"model":"fastgpt","choices":[{"index":0,"message":{"role":"assistant","content":"FastGPT answer"},"finish_reason":"stop"}],"usage":{"prompt_tokens":12,"completion_tokens":5,"total_tokens":17},"responseData":{"quoteList":[{"datasetName":"FastGPT 制度库","documentTitle":"Policy","content":"Policy quote","score":0.91,"chunkNo":2}]}}
                """, requestBody, authorization);
        try {
            FastGptRagClient client = new FastGptRagClient(createProperties(server), OBJECT_MAPPER);

            FastGptRagResult result = client.chat(FastGptRagRequest.builder()
                    .tenantId(1L)
                    .departmentId(20L)
                    .knowledgeBaseId(10L)
                    .conversationId(500L)
                    .userId(100L)
                    .question("hello")
                    .messages(List.of(AiChatModelMessage.builder()
                            .role(ChatMessageRoleEnum.USER.getCode())
                            .content("hello")
                            .build()))
                    .build());

            assertEquals("FastGPT answer", result.getModelResponse().getContent());
            assertEquals("fastgpt", result.getModelResponse().getModel());
            assertEquals(12, result.getModelResponse().getPromptTokens());
            assertEquals(5, result.getModelResponse().getCompletionTokens());
            assertEquals(17, result.getModelResponse().getTotalTokens());
            assertEquals("Bearer " + API_KEY_PLACEHOLDER, authorization.get());
            JsonNode requestJson = OBJECT_MAPPER.readTree(requestBody.get());
            assertEquals("fastgpt", requestJson.path("model").asText());
            assertEquals("tenant-1-conversation-500", requestJson.path("chatId").asText());
            assertEquals("100", requestJson.path("customUid").asText());
            assertTrue(requestJson.path("detail").asBoolean());
            assertEquals(ChatMessageRoleEnum.USER.getCode(), requestJson.path("messages").get(0).path("role").asText());
            assertEquals("hello", requestJson.path("messages").get(0).path("content").asText());
            assertEquals(1, result.getCitations().size());
            RagChatCitation citation = result.getCitations().get(0);
            assertEquals("FastGPT 制度库", citation.getKnowledgeBaseName());
            assertEquals("Policy", citation.getDocumentTitle());
            assertEquals("Policy quote", citation.getQuoteText());
            assertEquals(0.91D, citation.getScore());
            assertEquals(2, citation.getChunkNo());
            assertTrue(result.getDebugInfo().contains("FastGPT 平台层调试轨迹"));
            assertTrue(result.getDebugInfo().contains("citationCount=1"));
            assertTrue(result.getDebugInfo().contains("citationSource=fastgpt-quote-list"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void chatShouldParseCitationsFromResponseDataWorkflowNodes() throws Exception {
        HttpServer server = startServer(200, """
                {"id":"fastgpt-test","model":"fastgpt","choices":[{"message":{"role":"assistant","content":"answer"},"finish_reason":"stop","index":0}],"usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2},"responseData":[{"moduleName":"流程开始","moduleType":"workflowStart","runningTime":0},{"moduleName":"知识库搜索","moduleType":"datasetSearchNode","query":"开票信息","totalPoints":1,"quoteList":[{"dataset":{"name":"FastGPT 开票库"},"sourceName":"开票资料","q":"单位名称：理文科技","score":0.86,"chunkIndex":3}]},{"moduleName":"AI 对话","moduleType":"chatNode","model":"gpt-5.5","inputTokens":10,"outputTokens":20,"finishReason":"stop"}]}
                """, new AtomicReference<>(), new AtomicReference<>());
        try {
            FastGptRagClient client = new FastGptRagClient(createProperties(server), OBJECT_MAPPER);

            FastGptRagResult result = client.chat(buildRequest("开票信息"));

            assertEquals(1, result.getCitations().size());
            assertEquals("FastGPT 开票库", result.getCitations().get(0).getKnowledgeBaseName());
            assertEquals("开票资料", result.getCitations().get(0).getDocumentTitle());
            assertEquals("单位名称：理文科技", result.getCitations().get(0).getQuoteText());
            assertEquals(0.86D, result.getCitations().get(0).getScore());
            assertEquals(3, result.getCitations().get(0).getChunkNo());
            assertTrue(result.getDebugInfo().contains("知识库搜索"));
            assertTrue(result.getDebugInfo().contains("quoteListCount=1"));
            assertTrue(result.getDebugInfo().contains("scannedCitationArrayCount=1"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void chatShouldFallbackCitationFromAnswerLinks() throws Exception {
        HttpServer server = startServer(200, """
                {"id":"fastgpt-test","model":"fastgpt","choices":[{"message":{"role":"assistant","content":"结论。\\n![](http://127.0.0.1:13000/api/system/file/download/token?filename=%E5%BC%80%E7%A5%A8%E8%B5%84%E6%96%99.png)"},"finish_reason":"stop","index":0}],"usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2},"responseData":[{"moduleName":"知识库搜索","moduleType":"datasetSearchNode","quoteList":[]}]}
                """, new AtomicReference<>(), new AtomicReference<>());
        try {
            FastGptRagClient client = new FastGptRagClient(createProperties(server), OBJECT_MAPPER);

            FastGptRagResult result = client.chat(buildRequest("开票信息"));

            assertEquals(1, result.getCitations().size());
            assertEquals("开票资料.png", result.getCitations().get(0).getDocumentTitle());
            assertTrue(result.getCitations().get(0).getQuoteText().contains("/api/system/file/download"));
            assertTrue(result.getDebugInfo().contains("citationCount=1"));
            assertTrue(result.getDebugInfo().contains("citationSource=answer-link-fallback"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void chatShouldThrowConfigInvalidWhenMissingConfig() {
        FastGptRagClient client = new FastGptRagClient(new AiProperties(), OBJECT_MAPPER);

        ServiceException exception = assertThrows(ServiceException.class, () -> client.chat(FastGptRagRequest.builder()
                .tenantId(1L)
                .departmentId(20L)
                .knowledgeBaseId(10L)
                .conversationId(500L)
                .userId(100L)
                .question("hello")
                .messages(List.of(AiChatModelMessage.builder()
                        .role(ChatMessageRoleEnum.USER.getCode())
                        .content("hello")
                        .build()))
                .build()));

        assertEquals(RAG_ENGINE_CONFIG_INVALID, exception.getCode());
    }

    @Test
    void chatShouldThrowBusinessExceptionWhenRemoteFails() throws IOException {
        HttpServer server = startServer(500, "{\"error\":{\"message\":\"failed\"}}",
                new AtomicReference<>(), new AtomicReference<>());
        try {
            FastGptRagClient client = new FastGptRagClient(createProperties(server), OBJECT_MAPPER);

            ServiceException exception = assertThrows(ServiceException.class, () -> client.chat(FastGptRagRequest.builder()
                    .tenantId(1L)
                    .departmentId(20L)
                    .knowledgeBaseId(10L)
                    .conversationId(500L)
                    .userId(100L)
                    .question("hello")
                    .messages(List.of(AiChatModelMessage.builder()
                            .role(ChatMessageRoleEnum.USER.getCode())
                            .content("hello")
                            .build()))
                    .build()));

            assertEquals(RAG_ENGINE_REQUEST_FAILED, exception.getCode());
        } finally {
            server.stop(0);
        }
    }

    private HttpServer startServer(int statusCode, String responseBody, AtomicReference<String> requestBody,
                                   AtomicReference<String> authorization) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/chat/completions", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] responseBytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(statusCode, responseBytes.length);
            exchange.getResponseBody().write(responseBytes);
            exchange.close();
        });
        server.start();
        return server;
    }

    private FastGptRagRequest buildRequest(String question) {
        return FastGptRagRequest.builder()
                .tenantId(1L)
                .departmentId(20L)
                .knowledgeBaseId(10L)
                .conversationId(500L)
                .userId(100L)
                .question(question)
                .messages(List.of(AiChatModelMessage.builder()
                        .role(ChatMessageRoleEnum.USER.getCode())
                        .content(question)
                        .build()))
                .build();
    }

    private AiProperties createProperties(HttpServer server) {
        AiProperties properties = new AiProperties();
        properties.getFastgpt().setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.getFastgpt().setApiKey(API_KEY_PLACEHOLDER);
        properties.getFastgpt().setModel("fastgpt");
        properties.getFastgpt().setConnectTimeoutSeconds(2);
        properties.getFastgpt().setReadTimeoutSeconds(2);
        return properties;
    }

}
