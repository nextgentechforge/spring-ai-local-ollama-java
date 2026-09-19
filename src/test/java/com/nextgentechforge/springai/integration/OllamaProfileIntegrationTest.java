package com.nextgentechforge.springai.integration;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("ollama")
class OllamaProfileIntegrationTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final java.util.concurrent.ExecutorService EXECUTOR = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();
    private static final HttpServer PROVIDER = startProvider();
    private static final java.util.concurrent.atomic.AtomicInteger SEARCH_CALLS = new java.util.concurrent.atomic.AtomicInteger();
    @LocalServerPort int port;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.http.clients.read-timeout", () -> "1s");
        registry.add("spring.ai.retry.max-attempts", () -> 1);
        registry.add("app.web-search.base-url", () -> "http://127.0.0.1:" + PROVIDER.getAddress().getPort());
        registry.add("spring.ai.ollama.base-url", () -> "http://127.0.0.1:" + PROVIDER.getAddress().getPort());
    }
    @AfterAll static void stop() { PROVIDER.stop(0); EXECUTOR.shutdownNow(); }
    @Test void ollamaStartsAndAnswersWithoutOpenAiKey() throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/chat"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"message\":\"Explain dependency injection\"}")).build();
        var result = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(result.statusCode()).withFailMessage(result.body()).isEqualTo(200);
        assertThat(result.body()).contains("collaborators");
    }
    @Test void slowProviderReturnsServiceUnavailable() throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/chat"))
                .timeout(java.time.Duration.ofSeconds(8))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"message\":\"simulate-slow-provider\"}")).build();
        var result = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(result.statusCode()).withFailMessage(result.body()).isEqualTo(503);
        assertThat(result.body()).contains("The AI provider could not complete this request");
    }
    @Test void localModelCanSearchTheWebAndReceiveCitableSources() throws Exception {
        int before = SEARCH_CALLS.get();
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/chat"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"message\":\"internet-search-test: find Spring Boot official information\"}")).build();
        var result = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(result.statusCode()).withFailMessage(result.body()).isEqualTo(200);
        assertThat(result.body()).contains("https://spring.io/projects/spring-boot");
        assertThat(SEARCH_CALLS.get()).isGreaterThan(before);
    }
    @Test void directSearchEndpointReturnsSourcesAndValidatesInput() throws Exception {
        var http = HttpClient.newHttpClient();
        var good = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/web/search?q=Spring%20Boot")).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(good.statusCode()).isEqualTo(200);
        assertThat(good.body()).contains("https://spring.io/projects/spring-boot", "fetchedAt");
        var invalid = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/web/search?q=%20")).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(invalid.statusCode()).isEqualTo(400);
        var missing = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/web/search")).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(missing.statusCode()).isEqualTo(400);
    }
    private static HttpServer startProvider() {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(EXECUTOR);
            server.createContext("/search", exchange -> {
                SEARCH_CALLS.incrementAndGet();
                send(exchange, "{\"results\":[{\"title\":\"Spring Boot\",\"url\":\"https://spring.io/projects/spring-boot\",\"content\":\"Official Spring Boot project and documentation.\"}]}");
            });
            server.createContext("/api/embed", exchange -> {
                var request = JSON.readTree(exchange.getRequestBody());
                var input = request.path("input");
                var vectors = new ArrayList<double[]>();
                for (int i = 0; i < (input.isArray() ? input.size() : 1); i++) vectors.add(new double[]{1, 0, 0});
                send(exchange, JSON.writeValueAsString(Map.of("model", "nomic-embed-text", "embeddings", vectors)));
            });
            server.createContext("/api/chat", exchange -> {
                var request = JSON.readTree(exchange.getRequestBody());
                if (request.toString().contains("simulate-slow-provider")) {
                    try { Thread.sleep(3000); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return; }
                }
                if (!request.path("model").asText().equals("llama3.1:8b")) {
                    exchange.sendResponseHeaders(400, -1); exchange.close(); return;
                }
                if (request.toString().contains("internet-search-test")) {
                    boolean hasSearchResult = false;
                    for (var message : request.path("messages")) {
                        if (message.path("role").asText().equals("tool") && message.path("content").asText().contains("https://spring.io/projects/spring-boot")) {
                            hasSearchResult = true;
                        }
                    }
                    Map<String, Object> reply;
                    if (hasSearchResult) {
                        reply = Map.of("role", "assistant", "content", "See the official site: https://spring.io/projects/spring-boot");
                    } else {
                        reply = Map.of("role", "assistant", "content", "", "tool_calls", List.of(Map.of("function",
                                Map.of("name", "searchWeb", "arguments", Map.of("query", "Spring Boot official")))));
                    }
                    send(exchange, JSON.writeValueAsString(Map.of("model", "llama3.1:8b", "created_at", "2026-01-01T00:00:00Z",
                            "message", reply, "done", true, "done_reason", "stop")));
                    return;
                }
                send(exchange, JSON.writeValueAsString(Map.of("model", "llama3.1:8b", "created_at", "2026-01-01T00:00:00Z",
                        "message", Map.of("role", "assistant", "content", "Dependency injection supplies collaborators."),
                        "done", true, "done_reason", "stop")));
            });
            server.start(); return server;
        } catch (Exception exception) { throw new ExceptionInInitializerError(exception); }
    }
    private static void send(com.sun.net.httpserver.HttpExchange exchange, String body) throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (var output = exchange.getResponseBody()) { output.write(bytes); }
    }
}
