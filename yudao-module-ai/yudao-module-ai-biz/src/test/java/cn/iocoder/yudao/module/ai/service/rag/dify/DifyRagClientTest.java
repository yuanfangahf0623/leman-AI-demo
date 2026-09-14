package cn.iocoder.yudao.module.ai.service.rag.dify;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class DifyRagClientTest {
    private HttpServer server;
    private DifyRagClient client;
    private final ObjectMapper json = new ObjectMapper();
    private final AtomicReference<JsonNode> request = new AtomicReference<>();
    private final AtomicInteger calls = new AtomicInteger();
    private int status = 200;
    private String body = """
            {"answer":"测试答案","conversation_id":"external-conversation","metadata":{
              "usage":{"prompt_tokens":12,"completion_tokens":8,"total_tokens":20},
              "retriever_resources":[{"dataset_id":"allowed-dataset","document_name":"测试文档",
              "dataset_name":"测试知识库","content":"依据","score":0.9,"position":1}]}}
            """;

    @BeforeEach
    void setup() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat-messages", exchange -> {
            calls.incrementAndGet();
            request.set(json.readTree(exchange.getRequestBody()));
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        DifyProperties properties = new DifyProperties();
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setApiKey("test-placeholder");
        properties.setDatasetId("allowed-dataset");
        properties.setTenantId(1L);
        properties.setKnowledgeBaseId(10L);
        client = new DifyRagClient(properties, json);
    }

    @AfterEach
    void stop() { server.stop(0); }

    private DifyRagClient.Request valid() {
        return new DifyRagClient.Request(1L, 10L, 20L, 30L, "existing-conversation", "问题");
    }

    @Test
    void mapsAnswerCitationUsageAndScopedConversation() {
        var result = client.chat(valid());
        assertEquals("测试答案", result.modelResponse().getContent());
        assertEquals(20, result.modelResponse().getTotalTokens());
        assertEquals(10L, result.citations().get(0).getKnowledgeBaseId());
        assertNull(result.citations().get(0).getDocumentId());
        assertEquals("existing-conversation", request.get().path("conversation_id").asText());
        assertEquals("leman:t1:k10:u20:c30", request.get().path("user").asText());
    }

    @Test
    void deniesWrongTenantAndKnowledgeBeforeCallingDify() {
        assertThrows(ServiceException.class, () -> client.chat(
                new DifyRagClient.Request(2L, 10L, 20L, 30L, null, "问题")));
        assertThrows(ServiceException.class, () -> client.chat(
                new DifyRagClient.Request(1L, 11L, 20L, 30L, null, "问题")));
        assertEquals(0, calls.get());
    }

    @Test
    void rejectsUnboundCitationDataset() {
        body = body.replace("allowed-dataset", "another-dataset");
        assertThrows(ServiceException.class, () -> client.chat(valid()));
    }

    @Test
    void doesNotExposeUpstreamErrorBody() {
        status = 401;
        body = "private upstream diagnostic";
        var exception = assertThrows(ServiceException.class, () -> client.chat(valid()));
        assertFalse(exception.getMessage().contains(body));
    }

    @Test
    void rejectsMissingAnswer() {
        body = "{\"conversation_id\":\"c\"}";
        assertThrows(ServiceException.class, () -> client.chat(valid()));
    }
}
