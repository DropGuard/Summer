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
    private final NettyServerRunner server;
    private final HttpClient client =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    SseHttpIntegrationTest(NettyServerRunner server) {
        this.server = server;
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

    @DualEngine
    void handlerThrowingMidStreamStillTerminatesTheResponse() throws Exception {
        // Pre-fix: a handler throwing after taking an SseStream left it unclosed — no
        // LastHttpContent, so this read hung until the request timeout. The framework must
        // terminate the orphaned stream.
        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/test/sse/throwing"))
                        .timeout(Duration.ofSeconds(5))
                        .GET()
                        .build();

        HttpResponse<java.util.stream.Stream<String>> response =
                client.send(request, HttpResponse.BodyHandlers.ofLines());

        List<String> lines = response.body().filter(l -> !l.isBlank()).collect(Collectors.toList());
        assertEquals(1, lines.size(), "the event written before the throw must be delivered");
        assertTrue(lines.get(0).startsWith("data:"), "the partial event must be an SSE data line");
    }

    @DualEngine
    void clientDisconnectMidStreamReleasesTheDrainSlot() throws Exception {
        // The handler materialized a stream, sent one event, then waits for the client. Killing
        // the socket makes the channel inactive — isClosed() flips true WITHOUT close() ever
        // running, so pre-fix the drain slot leaked and every later shutdown of this server
        // instance drained for the full budget misreporting leftover streams.
        try (var socket = new java.net.Socket("localhost", server.getPort())) {
            socket.getOutputStream()
                    .write(
                            "GET /test/sse/hang-until-disconnect HTTP/1.1\r\nHost: localhost\r\n\r\n"
                                    .getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();

            var reader =
                    new java.io.BufferedReader(
                            new java.io.InputStreamReader(
                                    socket.getInputStream(),
                                    java.nio.charset.StandardCharsets.UTF_8));
            // Raw socket + chunked encoding: skip the response headers, then scan past the
            // chunk-size framing lines for the first SSE event.
            String line;
            boolean sawData = false;
            while ((line = reader.readLine()) != null) {
                if (line.equals("data: first")) {
                    sawData = true;
                    break;
                }
            }
            assertTrue(sawData, "the first SSE event must arrive");

            socket.close();
        }

        // The handler polls isClosed(), returns, and the orphan sweep releases the slot.
        long deadline = System.currentTimeMillis() + 5_000;
        while (server.getOpenStreams() > 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertEquals(
                0, server.getOpenStreams(), "the drain slot must be released after disconnect");
    }
}
