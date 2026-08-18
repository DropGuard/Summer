package com.github.dropguard.summer.web.server;

import static org.junit.jupiter.api.Assertions.*;

import com.github.dropguard.summer.test.annotation.DualEngine;
import com.github.dropguard.summer.test.annotation.SummerTest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

/** End-to-end integration test validating real TCP HTTP Chunked and SSE streaming with Netty. */
@SummerTest
class SseHttpIntegrationTest {

    private final String baseUrl;
    private final HttpClient client =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    SseHttpIntegrationTest(NettyServerRunner server) {
        this.baseUrl = "http://localhost:" + server.getPort();
    }

    @DualEngine
    void testOpenAiChatSseStream() throws Exception {
        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/test/openai/chat"))
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build();

        HttpResponse<java.util.stream.Stream<String>> response =
                client.send(request, HttpResponse.BodyHandlers.ofLines());

        assertEquals(200, response.statusCode());
        assertTrue(
                response.headers()
                        .firstValue("Content-Type")
                        .orElse("")
                        .contains("text/event-stream"));

        List<String> lines = response.body().filter(l -> !l.isBlank()).collect(Collectors.toList());

        assertEquals(6, lines.size());
        assertTrue(lines.get(0).startsWith("data: {\"choices\":"));
        assertTrue(lines.get(0).contains("Hello"));
        assertTrue(lines.get(4).contains("SSE"));
        assertEquals("data: [DONE]", lines.get(5));
    }

    @DualEngine
    void testChunkedCsvExport() throws Exception {
        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/test/stream/chunked"))
                        .GET()
                        .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.headers().firstValue("Content-Type").orElse("").contains("text/csv"));
        assertEquals("id,name\n1,Alice\n2,Bob\n", response.body());
    }
}
