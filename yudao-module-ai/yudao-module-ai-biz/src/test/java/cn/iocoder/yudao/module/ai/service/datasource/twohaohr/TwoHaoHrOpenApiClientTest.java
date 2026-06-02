package cn.iocoder.yudao.module.ai.service.datasource.twohaohr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TwoHaoHrOpenApiClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void fetchPagedObjectsByPostShouldUnwrapDataArray() throws Exception {
        HttpServer server = startServer("""
                {"errcode":0,"data":[{"id":"r1","dep_name":"生产制造部"}]}
                """);
        try {
            TwoHaoHrOpenApiClient client = new TwoHaoHrOpenApiClient(objectMapper,
                    name -> "TWO_HAO_HR_ACCESS_TOKEN".equals(name) ? "unit-test-token" : null,
                    HttpClient.newHttpClient());
            TwoHaoHrDataSourceConfig config = new TwoHaoHrDataSourceConfig();
            config.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
            config.setMaxPages(1);

            List<JsonNode> records = client.fetchPagedObjectsByPost(config, "/api/attendance/card_record/",
                    Map.of("limit", 100));

            assertEquals(1, records.size());
            assertEquals("r1", records.get(0).path("id").asText());
            assertEquals("生产制造部", records.get(0).path("dep_name").asText());
        } finally {
            server.stop(0);
        }
    }

    private HttpServer startServer(String responseBody) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/attendance/card_record/", exchange -> respond(exchange, responseBody));
        server.start();
        return server;
    }

    private void respond(HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

}
