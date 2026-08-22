package com.github.dropguard.summer.web.jsonb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.dropguard.summer.test.annotation.DualEngine;
import com.github.dropguard.summer.test.annotation.SummerTest;
import com.github.dropguard.summer.web.server.NettyServerRunner;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@SummerTest
class AvajeJsonbIntegrationTest {

    private final String baseUrl;
    private final HttpClient client =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    AvajeJsonbIntegrationTest(NettyServerRunner server) {
        this.baseUrl = "http://localhost:" + server.getPort();
    }

    @DualEngine
    void testGetPerson() throws Exception {
        HttpResponse<String> response =
                client.send(
                        HttpRequest.newBuilder().uri(URI.create(baseUrl + "/person")).GET().build(),
                        HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"name\":\"Charlie\""));
        assertTrue(response.body().contains("\"age\":28"));
    }

    @DualEngine
    void testPostPerson() throws Exception {
        String json = "{\"name\":\"David\",\"age\":35,\"email\":\"david@example.com\"}";
        HttpResponse<String> response =
                client.send(
                        HttpRequest.newBuilder()
                                .uri(URI.create(baseUrl + "/person"))
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString(json))
                                .build(),
                        HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"name\":\"David\""));
        assertTrue(response.body().contains("\"age\":35"));
    }
}
