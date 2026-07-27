package com.github.dropguard.summer.web.server;

import static org.junit.jupiter.api.Assertions.*;

import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.test.Testing;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HttpMiddlewareIntegrationTest {

    private static BeanContainer context;
    private static NettyServerRunner serverRunner;

    private final HttpClient client =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private String baseUrl;

    @BeforeAll
    void startServer() throws Exception {
        context = Testing.buildForTest(HttpMiddlewareIntegrationTest.class);
        serverRunner = context.getBean(NettyServerRunner.class);
        serverRunner.run(context);
        // Resolve the (possibly ephemeral) port from the runner instance, not a
        // JVM-global static — keeps this test independent of sibling IT classes.
        baseUrl = "http://localhost:" + serverRunner.getPort();
    }

    @AfterAll
    void stopServer() throws Exception {
        if (context != null) {
            context.close();
        }
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
