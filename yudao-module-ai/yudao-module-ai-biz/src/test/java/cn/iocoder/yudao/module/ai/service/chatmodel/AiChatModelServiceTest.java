package cn.iocoder.yudao.module.ai.service.chatmodel;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.ai.enums.ChatMessageRoleEnum;
import cn.iocoder.yudao.module.ai.framework.config.AiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static cn.iocoder.yudao.module.ai.enums.AiChatModelErrorCodeConstants.CHAT_MODEL_CONFIG_INVALID;
import static cn.iocoder.yudao.module.ai.enums.AiChatModelErrorCodeConstants.CHAT_MODEL_REQUEST_FAILED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiChatModelServiceTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String API_KEY_PLACEHOLDER = "${AI_API_KEY}";
    private static final String CHAT_MODEL = "unit-test-chat-model";

    @Test
    void mockChatModelServiceShouldReturnDeterministicAnswer() {
        AiChatModelService service = new MockChatModelService();
        AiChatModelRequest request = AiChatModelRequest.builder()
                .model("mock-rag-model")
                .systemPrompt("你是知识库助手")
                .userPrompt("什么是 RAG？")
                .build();

        AiChatModelResponse response = service.chat(request);

        assertEquals("mock-rag-model", response.getModel());
        assertEquals("stop", response.getFinishReason());
        assertEquals("Mock answer: 什么是 RAG？", response.getContent());
        assertTrue(response.getPromptTokens() > 0);
        assertTrue(response.getCompletionTokens() > 0);
        assertEquals(response.getPromptTokens() + response.getCompletionTokens(), response.getTotalTokens());
    }

    @Test
    void chatShouldCallLocalCompatibleEndpoint() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        HttpServer server = startServer(200, """
                {"id":"chatcmpl-test","object":"chat.completion","created":1710000000,"model":"unit-test-chat-model","choices":[{"index":0,"message":{"role":"assistant","content":"测试回答"},"finish_reason":"stop"}],"usage":{"prompt_tokens":12,"completion_tokens":3,"total_tokens":15}}
                """, requestBody, authorization);
        try {
            OpenAiCompatibleChatModelService service = new OpenAiCompatibleChatModelService(createProperties(server));

            AiChatModelResponse response = service.chat(AiChatModelRequest.builder()
                    .systemPrompt("你是知识库助手")
                    .userPrompt("请回答问题")
                    .temperature(0.2D)
                    .maxTokens(256)
                    .build());

            assertEquals("测试回答", response.getContent());
            assertEquals("stop", response.getFinishReason());
            assertEquals(CHAT_MODEL, response.getModel());
            assertEquals(12, response.getPromptTokens());
            assertEquals(3, response.getCompletionTokens());
            assertEquals(15, response.getTotalTokens());
            assertEquals("chatcmpl-test", response.getMetadata().get("id"));
            assertEquals("Bearer " + API_KEY_PLACEHOLDER, authorization.get());
            JsonNode requestJson = OBJECT_MAPPER.readTree(requestBody.get());
            assertEquals(CHAT_MODEL, requestJson.path("model").asText());
            assertEquals(0.2D, requestJson.path("temperature").asDouble());
            assertEquals(256, requestJson.path("max_tokens").asInt());
            assertEquals(ChatMessageRoleEnum.SYSTEM.getCode(), requestJson.path("messages").get(0).path("role").asText());
            assertEquals("你是知识库助手", requestJson.path("messages").get(0).path("content").asText());
            assertEquals(ChatMessageRoleEnum.USER.getCode(), requestJson.path("messages").get(1).path("role").asText());
            assertEquals("请回答问题", requestJson.path("messages").get(1).path("content").asText());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void constructorShouldNotFailWhenConfigMissing() {
        AiProperties properties = new AiProperties();

        OpenAiCompatibleChatModelService service = assertDoesNotThrow(() -> new OpenAiCompatibleChatModelService(properties));
        ServiceException exception = assertThrows(ServiceException.class, () -> service.chat(AiChatModelRequest.builder()
                .userPrompt("hello")
                .build()));

        assertEquals(CHAT_MODEL_CONFIG_INVALID, exception.getCode());
    }

    @Test
    void chatShouldThrowBusinessExceptionWhenRemoteFails() throws IOException {
        HttpServer server = startServer(500, "{\"error\":{\"message\":\"failed\"}}",
                new AtomicReference<>(), new AtomicReference<>());
        try {
            OpenAiCompatibleChatModelService service = new OpenAiCompatibleChatModelService(createProperties(server));

            ServiceException exception = assertThrows(ServiceException.class, () -> service.chat(AiChatModelRequest.builder()
                    .messages(List.of(AiChatModelMessage.builder()
                            .role(ChatMessageRoleEnum.USER.getCode())
                            .content("hello")
                            .build()))
                    .build()));

            assertEquals(CHAT_MODEL_REQUEST_FAILED, exception.getCode());
        } finally {
            server.stop(0);
        }
    }

    private HttpServer startServer(int statusCode, String responseBody, AtomicReference<String> requestBody,
                                   AtomicReference<String> authorization) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
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

    private AiProperties createProperties(HttpServer server) {
        AiProperties properties = new AiProperties();
        properties.getModel().setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1");
        properties.getModel().setApiKey(API_KEY_PLACEHOLDER);
        properties.getModel().setChatModel(CHAT_MODEL);
        properties.getModel().setConnectTimeoutSeconds(2);
        properties.getModel().setReadTimeoutSeconds(2);
        return properties;
    }

}
