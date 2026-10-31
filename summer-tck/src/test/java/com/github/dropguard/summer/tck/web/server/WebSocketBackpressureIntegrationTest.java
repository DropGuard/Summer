package com.github.dropguard.summer.tck.web.server;

import static org.junit.jupiter.api.Assertions.*;

import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.tck.web.fixtures.BackpressureWsRouteProvider;
import com.github.dropguard.summer.tck.web.fixtures.BackpressureWsRouteProvider.Gate;
import com.github.dropguard.summer.test.annotation.DualEngine;
import com.github.dropguard.summer.test.annotation.SummerTest;
import com.github.dropguard.summer.web.server.NettyServerRunner;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

/**
 * Transport-level contract for the WebSocket inbound pump: a blocking message handler must stall
 * only its own connection. While the handler is parked, the event loop keeps serving HTTP; a burst
 * larger than the inbound queue capacity must be absorbed by read suspension (TCP backpressure) —
 * not by the 1008 overflow close — and delivered complete and in order once the handler catches up.
 */
@SummerTest
class WebSocketBackpressureIntegrationTest {

    /**
     * Larger than the default {@code server.ws-message-queue-capacity} (64): if reads were not
     * suspended, the queue would overflow and the connection would be closed with 1008 long before
     * the burst finished.
     */
    private static final int BURST = 200;

    @DualEngine
    void slowHandlerBlocksOnlyItsOwnConnection(BeanContainer container) throws Exception {
        // The invocation's own server — the assertions must hit the engine-under-test.
        int enginePort = container.getBean(NettyServerRunner.class).getPort();
        Gate gate = new Gate();
        BackpressureWsRouteProvider.arm(gate);

        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        AckCollector acks = new AckCollector(BURST + 1);
        WebSocket ws =
                client.newWebSocketBuilder()
                        .buildAsync(
                                URI.create("ws://localhost:" + enginePort + "/ws-backpressure"),
                                acks)
                        .get(5, TimeUnit.SECONDS);

        ws.sendText("m:0", true).get(5, TimeUnit.SECONDS);
        gate.awaitFirstMessage();
        assertTrue(
                gate.handlerThread().isVirtual(),
                "message handlers must run on a virtual thread, not the event loop");

        // The handler is parked inside the gate. The event loop must still serve everyone else —
        // under the old loop-thread model this request would block until the gate opened.
        HttpRequest probe =
                HttpRequest.newBuilder(URI.create("http://localhost:" + enginePort + "/rt/items"))
                        .timeout(Duration.ofSeconds(2))
                        .build();
        for (int i = 0; i < 5; i++) {
            HttpResponse<String> response =
                    client.send(probe, HttpResponse.BodyHandlers.ofString());
            assertEquals(
                    200,
                    response.statusCode(),
                    "HTTP must stay responsive while a WS" + " handler blocks");
        }
        assertTrue(gate.acked().isEmpty(), "the parked handler must not have acknowledged yet");

        // Burst beyond the queue capacity, paced: the point is that read suspension absorbs it.
        // The sleep is rate limiting, not synchronization on a condition.
        for (int i = 1; i <= BURST; i++) {
            ws.sendText("m:" + i, true);
            Thread.sleep(5);
        }

        gate.open();
        acks.allAcked.get(20, TimeUnit.SECONDS);
        // The test universe carries a global WebSocketInterceptor (WebSocketInterceptorTestConfig)
        // that prefixes every message — the expected prefix doubles as proof that the interceptor
        // chain runs on the connection's pump thread, serialized with the user handler. The gate
        // records the raw (intercepted) inbound message; the "ACK " prefix only goes to the client.
        List<String> expected =
                IntStream.rangeClosed(0, BURST).mapToObj(i -> "[INTERCEPTED] m:" + i).toList();
        assertEquals(
                expected,
                gate.acked(),
                "every message must be processed, in send order, with no 1008 close");

        ws.sendClose(WebSocket.NORMAL_CLOSURE, "done").get(5, TimeUnit.SECONDS);
        assertTrue(
                gate.awaitClosed(5000),
                "onClose must fire after the last buffered message, on the connection thread");
    }

    /** Collects server acknowledgements; completes once the expected count has arrived. */
    private static final class AckCollector implements WebSocket.Listener {

        private final ConcurrentLinkedQueue<String> received = new ConcurrentLinkedQueue<>();
        private final CompletableFuture<Void> allAcked = new CompletableFuture<>();
        private final int total;

        AckCollector(int total) {
            this.total = total;
        }

        @Override
        public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            if (last) {
                received.add(data.toString());
                if (received.size() >= total) {
                    allAcked.complete(null);
                }
                ws.request(1);
            }
            return null;
        }
    }
}
