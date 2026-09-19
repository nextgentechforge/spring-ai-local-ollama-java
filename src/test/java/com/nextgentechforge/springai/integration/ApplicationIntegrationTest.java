package com.nextgentechforge.springai.integration;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

/** Exercises real HTTP, provider adapters, startup indexing, advisor and tool execution.
 * The stub is deterministic; it does not establish real model answer quality. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("openai")
class ApplicationIntegrationTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final AtomicInteger TOOL_RESULTS = new AtomicInteger();
    private static final AtomicInteger EMBEDDINGS = new AtomicInteger();
    private static final HttpServer PROVIDER = startProvider();
    @LocalServerPort int port;
    private final HttpClient http = HttpClient.newHttpClient();

    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.ai.openai.api-key", () -> "test-only-placeholder");
        registry.add("spring.ai.openai.base-url", () -> "http://127.0.0.1:" + PROVIDER.getAddress().getPort() + "/v1");
        registry.add("app.rag.top-k", () -> 20);
        registry.add("app.rag.similarity-threshold", () -> 0.0);
    }
    @AfterAll static void stopProvider() { PROVIDER.stop(0); }

    @Test void startsAndServesHealth() throws Exception {
        var result = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/health")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(result.statusCode()).isEqualTo(200);
        assertThat(result.body()).contains("UP");
        assertThat(EMBEDDINGS.get()).isPositive();
    }
    @Test void basicChatTraversesProviderAdapter() throws Exception {
        assertThat(chat("Explain dependency injection in one paragraph.")).contains("collaborators");
    }
    @Test void architectureIsPassedFromMarkdownThroughRag() throws Exception {
        assertThat(chat("How does our payment-service communicate with order-service?")).contains("Kafka", "orders.created.v1");
    }
    @Test void rollbackGuideIsPassedThroughRag() throws Exception {
        assertThat(chat("How do we roll back a failed deployment?")).contains("previous known-good ECS task definition");
    }
    @Test void modelToolRequestInvokesJavaAndReturnsResultToModel() throws Exception {
        int before = TOOL_RESULTS.get();
        assertThat(chat("What version of payment-service is currently running?")).contains("2.4.1", "simulated");
        assertThat(TOOL_RESULTS.get()).isGreaterThan(before);
    }
    private String chat(String message) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/chat"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(java.util.Map.of("message", message)))).build();
        var response = http.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(200);
        return JSON.readTree(response.body()).path("answer").asText();
    }
    private static HttpServer startProvider() {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/v1/embeddings", exchange -> {
                var input = JSON.readTree(exchange.getRequestBody()).path("input");
                int count = input.isArray() ? input.size() : 1;
                var data = new java.util.ArrayList<java.util.Map<String, Object>>();
                for (int i = 0; i < count; i++) data.add(java.util.Map.of("object", "embedding", "index", i, "embedding", new double[]{1, 0, 0}));
                EMBEDDINGS.addAndGet(count);
                send(exchange, JSON.writeValueAsString(java.util.Map.of("object", "list", "data", data,
                        "model", "test-embedding", "usage", java.util.Map.of("prompt_tokens", 1, "total_tokens", 1))));
            });
            server.createContext("/v1/chat/completions", exchange -> {
                JsonNode request = JSON.readTree(exchange.getRequestBody());
                String body = request.toString();
                boolean toolResult = false;
                for (JsonNode message : request.path("messages")) {
                    if (message.path("role").asText().equals("tool")) {
                        toolResult = message.path("content").asText().contains("2.4.1");
                    }
                }
                if (toolResult) {
                    TOOL_RESULTS.incrementAndGet();
                    send(exchange, completion("payment-service version is 2.4.1 (simulated)."));
                } else if (body.contains("What version of payment-service")) {
                    var function = java.util.Map.of("name", "getDeploymentVersion",
                            "arguments", JSON.writeValueAsString(java.util.Map.of("service", "payment-service")));
                    var call = java.util.Map.of("id", "call_1", "type", "function", "function", function);
                    var message = java.util.Map.of("role", "assistant", "tool_calls", java.util.List.of(call));
                    send(exchange, JSON.writeValueAsString(java.util.Map.of("id", "test", "object", "chat.completion",
                            "created", 1, "model", "test", "choices", java.util.List.of(java.util.Map.of(
                                    "index", 0, "finish_reason", "tool_calls", "message", message)))));
                } else if (body.contains("How does our payment-service")) {
                    send(exchange, completion(body.contains("orders.created.v1") ? "Kafka connects the services via orders.created.v1 (Architecture)." : "MISSING CONTEXT"));
                } else if (body.contains("How do we roll back")) {
                    send(exchange, completion(body.contains("previous known-good ECS task definition") ? "Select the previous known-good ECS task definition and verify health (Deployment guide)." : "MISSING CONTEXT"));
                } else {
                    send(exchange, completion("Dependency injection supplies collaborators to an object."));
                }
            });
            server.start();
            return server;
        } catch (IOException exception) { throw new ExceptionInInitializerError(exception); }
    }
    private static String completion(String answer) {
        return JSON.writeValueAsString(java.util.Map.of("id", "test", "object", "chat.completion", "created", 1, "model", "test",
                "choices", java.util.List.of(java.util.Map.of("index", 0, "finish_reason", "stop", "message", java.util.Map.of("role", "assistant", "content", answer)))));
    }
    private static void send(com.sun.net.httpserver.HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (var output = exchange.getResponseBody()) { output.write(bytes); }
    }
}
