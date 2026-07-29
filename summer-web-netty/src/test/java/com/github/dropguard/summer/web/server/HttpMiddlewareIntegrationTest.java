package com.github.dropguard.summer.web.server;

import static org.junit.jupiter.api.Assertions.*;

import com.github.dropguard.summer.test.annotation.SummerTest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

@SummerTest
class HttpMiddlewareIntegrationTest {

    private final String baseUrl;

    private final HttpClient client =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    HttpMiddlewareIntegrationTest(NettyServerRunner server) {
        this.baseUrl = "http://localhost:" + server.getPort();
    }

    @Test
    void testMiddlewareInterceptsAndModifiesResponse() throws Exception {
        HttpResponse<String> response =
                client.send(
                        HttpRequest.newBuilder()
                                .uri(URI.create(baseUrl + "/test/hello"))
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertEquals("world", response.body());

        List<String> middlewareHeader = response.headers().allValues("X-Test-Middleware");
        assertNotNull(middlewareHeader);
        assertEquals(1, middlewareHeader.size());
        assertEquals("Active", middlewareHeader.get(0));
    }
}
