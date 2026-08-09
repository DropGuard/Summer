package com.github.dropguard.summer.tck.web.server;

import static org.junit.jupiter.api.Assertions.*;

import com.github.dropguard.summer.test.annotation.DualEngine;
import com.github.dropguard.summer.test.annotation.SummerTest;
import com.github.dropguard.summer.web.server.NettyServerRunner;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

@SummerTest
class WebSocketInterceptorIntegrationTest {

    @DualEngine
    void testInterceptorModifiesMessage(com.github.dropguard.summer.core.BeanContainer container)
            throws Exception {
        // The invocation's own server (see WebSocketBroadcasterTest): the AOT invocation must hit
        // the
        // AOT container's Netty server, not the RUNTIME instance's.
        int enginePort = container.getBean(NettyServerRunner.class).getPort();
        var received = new CompletableFuture<String>();
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

        WebSocket ws =
                client.newWebSocketBuilder()
                        .buildAsync(
                                URI.create("ws://localhost:" + enginePort + "/ws-test"),
                                new WebSocket.Listener() {
                                    public CompletionStage<?> onText(
                                            WebSocket w, CharSequence data, boolean last) {
                                        received.complete(data.toString());
                                        return null;
                                    }
                                })
                        .get(5, TimeUnit.SECONDS);

        ws.sendText("ping", true);
        assertEquals(
                "[INTERCEPTED] ping",
                received.get(5, TimeUnit.SECONDS),
                "message should be prefixed by the WebSocket interceptor");
    }
}
