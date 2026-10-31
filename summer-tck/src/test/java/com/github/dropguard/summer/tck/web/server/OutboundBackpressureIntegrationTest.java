package com.github.dropguard.summer.tck.web.server;

import static org.junit.jupiter.api.Assertions.*;

import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.test.annotation.DualEngine;
import com.github.dropguard.summer.test.annotation.SummerTest;
import com.github.dropguard.summer.test.annotation.TestProfile;
import com.github.dropguard.summer.web.server.NettyServerRunner;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Transport-level contract for outbound backpressure: a peer that stops reading must hit the
 * outbound stall budget ({@code server.outbound-send-timeout}, shortened to 500ms here) and be
 * closed — while readers on the same paths keep streaming and the broadcaster never waits on the
 * stalled member. A stalled stream only ends by watchdog close, so EOF is the observable.
 */
@SummerTest
@TestProfile(OutboundStallProfile.class)
class OutboundBackpressureIntegrationTest {

    private static final Duration WS_CONNECT = Duration.ofSeconds(5);

    @DualEngine
    void stalledWsMemberIsClosedAtTheStallBudget(BeanContainer container) throws Exception {
        int port = container.getBean(NettyServerRunner.class).getPort();
        Socket staller = openRawWebSocket(port, "/ws-flood");

        writeMaskedTextFrame(staller, "flood");

        stallThenEof(staller, 1500, 5000);
    }

    @DualEngine
    void broadcastKeepsFlowingToReadersWhileStalledMemberIsClosed(BeanContainer container)
            throws Exception {
        int port = container.getBean(NettyServerRunner.class).getPort();
        Socket staller = openRawWebSocket(port, "/ws-flood");

        AtomicLong firstTick = new AtomicLong();
        AtomicLong lastTick = new AtomicLong();
        AtomicLong ticks = new AtomicLong();
        HttpClient client = HttpClient.newBuilder().connectTimeout(WS_CONNECT).build();
        WebSocket reader =
                client.newWebSocketBuilder()
                        .buildAsync(
                                URI.create("ws://localhost:" + port + "/ws-flood"),
                                new WebSocket.Listener() {
                                    public CompletionStage<?> onText(
                                            WebSocket ws, CharSequence data, boolean last) {
                                        long now = System.nanoTime();
                                        firstTick.compareAndSet(0, now);
                                        lastTick.set(now);
                                        ticks.incrementAndGet();
                                        ws.request(1);
                                        return null;
                                    }
                                })
                        .get(WS_CONNECT.toSeconds(), TimeUnit.SECONDS);

        reader.sendText("flood", true).get(5, TimeUnit.SECONDS);

        // The staller pins within the first second; the reader must keep receiving well beyond
        // the 500ms budget — a broadcaster that waited on the stalled member would stop at ~0.
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            long windowMs = TimeUnit.NANOSECONDS.toMillis(lastTick.get() - firstTick.get());
            if (firstTick.get() > 0 && windowMs >= 800) {
                break;
            }
            Thread.sleep(25);
        }
        long windowMs = TimeUnit.NANOSECONDS.toMillis(lastTick.get() - firstTick.get());
        assertTrue(
                windowMs >= 800,
                "reader must keep receiving past the stall budget, window was " + windowMs + "ms");
        assertTrue(ticks.get() > 100, "reader must have received real volume");

        stallThenEof(staller, 1500, 5000);
        reader.abort();
    }

    @DualEngine
    void stalledSseClientIsClosedAtTheStallBudget(BeanContainer container) throws Exception {
        int port = container.getBean(NettyServerRunner.class).getPort();
        Socket staller = openRawGet(port, "/rt/sse/flood");

        stallThenEof(staller, 1500, 5000);
    }

    @DualEngine
    void readingClientStreamsUnaffected(BeanContainer container) throws Exception {
        int port = container.getBean(NettyServerRunner.class).getPort();
        HttpClient client = HttpClient.newBuilder().connectTimeout(WS_CONNECT).build();
        HttpResponse<java.io.InputStream> response =
                client.send(
                        HttpRequest.newBuilder(
                                        URI.create("http://localhost:" + port + "/rt/sse/flood"))
                                .build(),
                        HttpResponse.BodyHandlers.ofInputStream());

        long start = System.currentTimeMillis();
        try (var body = response.body()) {
            // The flood has no newlines, so count raw reads (~one 16KB chunk each) — never
            // readLine(), which would accumulate the unbounded stream into one string.
            byte[] buf = new byte[16 * 1024];
            int reads = 0;
            while (reads < 200 && body.read(buf) >= 0) {
                reads++;
            }
            assertEquals(200, reads, "a reading client must receive the stream without stalls");
        }
        long elapsedMs = System.currentTimeMillis() - start;
        assertTrue(elapsedMs < 5000, "200 chunks must arrive promptly, took " + elapsedMs + "ms");
    }

    // ── raw-socket helpers ──────────────────────────────────────────────────────────

    /**
     * Opens the connection, sends the request and consumes the response head; the caller decides
     * what (not) to read afterwards. Used for both the WS handshake (expecting 101) and raw SSE
     * (expecting 200).
     */
    private static Socket openRawGet(int port, String path) throws IOException {
        Socket socket = new Socket("localhost", port);
        socket.setSoTimeout(5000);
        socket.getOutputStream()
                .write(
                        ("GET " + path + " HTTP/1.1\r\nHost: localhost\r\n\r\n")
                                .getBytes(StandardCharsets.ISO_8859_1));
        readHead(socket.getInputStream());
        socket.setSoTimeout(0);
        return socket;
    }

    /** Completes the RFC 6455 handshake by hand; the caller then never reads. */
    private static Socket openRawWebSocket(int port, String path) throws IOException {
        Socket socket = new Socket("localhost", port);
        socket.setSoTimeout(5000);
        socket.getOutputStream()
                .write(
                        ("GET "
                                        + path
                                        + " HTTP/1.1\r\n"
                                        + "Host: localhost\r\n"
                                        + "Upgrade: websocket\r\n"
                                        + "Connection: Upgrade\r\n"
                                        + "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n"
                                        + "Sec-WebSocket-Version: 13\r\n\r\n")
                                .getBytes(StandardCharsets.ISO_8859_1));
        readHead(socket.getInputStream());
        socket.setSoTimeout(0);
        return socket;
    }

    private static void writeMaskedTextFrame(Socket socket, String text) throws IOException {
        byte[] payload = text.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream frame = new ByteArrayOutputStream();
        frame.write(0x81); // FIN + text opcode
        int len = payload.length;
        if (len < 126) {
            frame.write(0x80 | len);
        } else {
            frame.write(0x80 | 126);
            frame.write(len >> 8);
            frame.write(len & 0xFF);
        }
        byte[] mask = {0x11, 0x22, 0x33, 0x44};
        frame.write(mask);
        for (int i = 0; i < len; i++) {
            frame.write(payload[i] ^ mask[i & 3]);
        }
        socket.getOutputStream().write(frame.toByteArray());
        socket.getOutputStream().flush();
    }

    /** Reads the response head (until the blank line); the status line must be a 101 or 200. */
    private static void readHead(InputStream in) throws IOException {
        StringBuilder head = new StringBuilder();
        int c;
        while ((c = in.read()) >= 0) {
            head.append((char) c);
            if (head.length() >= 4 && head.substring(head.length() - 4).equals("\r\n\r\n")) {
                break;
            }
        }
        String status = head.substring(0, Math.min(12, head.length()));
        assertTrue(
                status.startsWith("HTTP/1.1 101") || status.startsWith("HTTP/1.1 200"),
                "unexpected handshake response: " + head.substring(0, Math.min(64, head.length())));
    }

    /**
     * Creates a genuine stall: {@code stallMs} of complete silence (no reads at all — draining
     * while waiting would keep the peer's socket drained and the server would never stall), then
     * drains until EOF. Returns ms from the end of the silent window to the EOF: a watchdog-closed
     * connection reaches EOF within milliseconds; a never-closed one times out and fails.
     */
    private static long stallThenEof(Socket socket, long stallMs, long drainDeadlineMs)
            throws IOException, InterruptedException {
        Thread.sleep(stallMs);
        socket.setSoTimeout((int) drainDeadlineMs);
        long start = System.currentTimeMillis();
        try {
            byte[] buf = new byte[8192];
            while (socket.getInputStream().read(buf) >= 0) {
                // drain
            }
            return System.currentTimeMillis() - start;
        } catch (java.net.SocketTimeoutException e) {
            fail(
                    "no EOF within "
                            + drainDeadlineMs
                            + " ms — the stalled connection was never closed");
            return -1;
        }
    }
}
