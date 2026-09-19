package com.nextgentechforge.springai.service;

import com.nextgentechforge.springai.config.WebSearchConfig;
import com.nextgentechforge.springai.dto.WebSearchResponse.Status;
import com.nextgentechforge.springai.tools.WebSearchTools;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.web.client.RestClient;
import static org.assertj.core.api.Assertions.*;

class WebSearchServiceTest {
    private HttpServer server;
    private RestClient client;
    private WebSearchService service;
    private String body;
    private int status;
    private final AtomicReference<String> request = new AtomicReference<>();
    private final AtomicInteger calls = new AtomicInteger();
    private final Instant now = Instant.parse("2026-09-19T12:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);

    @BeforeEach void setup() throws Exception {
        status = 200;
        body = """
                {"results":[{"title":"Spring Boot","url":"https://spring.io/projects/spring-boot",
                "content":"Official <b>Spring</b> &amp; Java information"}]}
                """;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/search", exchange -> {
            calls.incrementAndGet(); request.set(exchange.getRequestURI().getRawQuery());
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
        server.start();
        client = new WebSearchConfig().webSearchClient(URI.create("http://127.0.0.1:" + server.getAddress().getPort()), Duration.ofSeconds(2));
        service = new WebSearchService(client, clock, true, 3);
    }
    @AfterEach void stop() { server.stop(0); }

    @Test void returnsRealSourceMetadataAndEncodesQueryAsData() {
        var result = service.search("Spring & Java + releases?");
        assertThat(result.status()).isEqualTo(Status.OK);
        assertThat(result.fetchedAt()).isEqualTo(now);
        assertThat(result.sources().getFirst().snippet()).isEqualTo("Official Spring & Java information");
        assertThat(request.get()).contains("q=Spring%20%26%20Java%20%2B%20releases%3F", "format=json");
    }
    @Test void filtersUnsafeLinksDeduplicatesAndLimitsResults() {
        body = """
                {"results":[
                {"title":"Bad","url":"javascript:alert(1)"},
                {"title":"File","url":"file:///etc/passwd"},
                {"title":"Credentials","url":"https://user:secret@example.org/"},
                {"title":"One","url":"https://one.example/"},
                {"title":"Duplicate","url":"https://one.example/"},
                {"title":"Two","url":"https://two.example/"},
                {"title":"Three","url":"https://three.example/"},
                {"title":"Four","url":"https://four.example/"}]}
                """;
        assertThat(service.search("test").sources()).extracting(s -> s.title()).containsExactly("One", "Two", "Three");
    }
    @Test void boundsTheContextSentToTheModel() {
        body = "{\"results\":[{\"title\":\"" + "t".repeat(300)
                + "\",\"url\":\"https://example.org/\",\"content\":\"" + "x".repeat(1000) + "\"}]}";
        var source = service.search("test").sources().getFirst();
        assertThat(source.title()).hasSize(200);
        assertThat(source.snippet()).hasSize(600);
    }
    @Test void distinguishesNoResultsFromFailedEngines() {
        body = "{\"results\":[]}";
        assertThat(service.search("test").status()).isEqualTo(Status.NO_RESULTS);
        body = "{\"results\":[],\"unresponsive_engines\":[[\"engine\",\"timeout\"]]}";
        assertThat(service.search("test").status()).isEqualTo(Status.UNAVAILABLE);
    }
    @Test void unavailableProviderDoesNotLeakItsResponse() {
        status = 503; body = "private-backend-details";
        var result = service.search("test");
        assertThat(result.status()).isEqualTo(Status.UNAVAILABLE);
        assertThat(result.note()).doesNotContain(body);
        assertThat(result.sources()).isEmpty();
    }
    @Test void rejectsMalformedSearchPayload() {
        body = "not json";
        assertThat(service.search("test").status()).isEqualTo(Status.UNAVAILABLE);
        body = "{}";
        assertThat(service.search("test").status()).isEqualTo(Status.UNAVAILABLE);
    }
    @Test void invalidAndDisabledRequestsNeverReachTheNetwork() {
        assertThat(service.search(null).status()).isEqualTo(Status.INVALID_QUERY);
        assertThat(service.search(" ").status()).isEqualTo(Status.INVALID_QUERY);
        assertThat(service.search("x".repeat(301)).status()).isEqualTo(Status.INVALID_QUERY);
        assertThat(new WebSearchService(client, clock, false, 3).search("test").status()).isEqualTo(Status.DISABLED);
        assertThat(calls.get()).isZero();
    }
    @Test void springAiInvokesTheSearchTool() {
        var callback = ToolCallbacks.from(new WebSearchTools(service))[0];
        assertThat(callback.getToolDefinition().name()).isEqualTo("searchWeb");
        assertThat(callback.call("{\"query\":\"Spring Boot\"}")).contains("https://spring.io/projects/spring-boot");
        assertThat(calls.get()).isEqualTo(1);
    }
}
