package cn.iocoder.yudao.module.ai.service.embedding;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
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

import static cn.iocoder.yudao.module.ai.enums.AiEmbeddingErrorCodeConstants.EMBEDDING_CONFIG_INVALID;
import static cn.iocoder.yudao.module.ai.enums.AiEmbeddingErrorCodeConstants.EMBEDDING_REQUEST_FAILED;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiCompatibleEmbeddingServiceTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String API_KEY_PLACEHOLDER = "${AI_API_KEY}";
    private static final String EMBEDDING_MODEL = "unit-test-embedding-model";

    @Test
    void embedBatchShouldCallLocalCompatibleEndpoint() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        HttpServer server = startServer(200,
                "{\"data\":[{\"index\":0,\"embedding\":[0.1,0.2]},{\"index\":1,\"embedding\":[0.3,0.4]}]}",
                requestBody, authorization);
        try {
            OpenAiCompatibleEmbeddingService service = new OpenAiCompatibleEmbeddingService(createProperties(server));

            List<List<Double>> embeddings = service.embedBatch(List.of("first", "second"));

            assertEquals(List.of(0.1D, 0.2D), embeddings.get(0));
            assertEquals(List.of(0.3D, 0.4D), embeddings.get(1));
            assertEquals("Bearer " + API_KEY_PLACEHOLDER, authorization.get());
            JsonNode requestJson = OBJECT_MAPPER.readTree(requestBody.get());
            assertEquals(EMBEDDING_MODEL, requestJson.path("model").asText());
            assertTrue(requestJson.path("input").isArray());
            assertEquals("first", requestJson.path("input").get(0).asText());
            assertEquals("second", requestJson.path("input").get(1).asText());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void embedShouldCallLocalCompatibleEndpoint() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = startServer(200,
                "{\"data\":[{\"index\":0,\"embedding\":[1.0,2.0,3.0]}]}",
                requestBody, new AtomicReference<>());
        try {
            OpenAiCompatibleEmbeddingService service = new OpenAiCompatibleEmbeddingService(createProperties(server));

            List<Double> embedding = service.embed("hello");

            assertEquals(List.of(1.0D, 2.0D, 3.0D), embedding);
            JsonNode requestJson = OBJECT_MAPPER.readTree(requestBody.get());
            assertEquals("hello", requestJson.path("input").asText());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void constructorShouldNotFailWhenConfigMissing() {
        AiProperties properties = new AiProperties();

        OpenAiCompatibleEmbeddingService service = assertDoesNotThrow(() -> new OpenAiCompatibleEmbeddingService(properties));
        ServiceException exception = assertThrows(ServiceException.class, () -> service.embed("hello"));

        assertEquals(EMBEDDING_CONFIG_INVALID, exception.getCode());
    }

    @Test
    void embedShouldThrowBusinessExceptionWhenRemoteFails() throws IOException {
        HttpServer server = startServer(500, "{\"error\":{\"message\":\"failed\"}}",
                new AtomicReference<>(), new AtomicReference<>());
        try {
            OpenAiCompatibleEmbeddingService service = new OpenAiCompatibleEmbeddingService(createProperties(server));

            ServiceException exception = assertThrows(ServiceException.class, () -> service.embed("hello"));

            assertEquals(EMBEDDING_REQUEST_FAILED, exception.getCode());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void mockEmbeddingServiceShouldReturnConfiguredDimensions() {
        AiEmbeddingService service = new MockEmbeddingService(7);

        List<Double> embedding = service.embed("hello");
        List<List<Double>> batch = service.embedBatch(List.of("a", "b"));

        assertEquals(7, embedding.size());
        assertEquals(2, batch.size());
        assertEquals(7, batch.get(0).size());
        assertInstanceOf(MockEmbeddingService.class, service);
    }

    private HttpServer startServer(int statusCode, String responseBody, AtomicReference<String> requestBody,
                                   AtomicReference<String> authorization) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/embeddings", exchange -> {
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
        properties.getModel().setEmbeddingModel(EMBEDDING_MODEL);
        properties.getModel().setConnectTimeoutSeconds(2);
        properties.getModel().setReadTimeoutSeconds(2);
        return properties;
    }

}
