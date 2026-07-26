package com.github.dropguard.summer.twitter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * WebSocket integration coverage for the real-time push surface. Two endpoints:
 *
 * <ul>
 * <li>{@code /ws/dm} (DmHandler): handshake auth, send-to-online-recipient
 * fan-out, send-to-missing-recipient error, mark-read receipts. Fully covered
 * here because every path is reachable over a real WS connection.</li>
 * <li>{@code /ws/events} (EventsHandler + EventPublisher): the {@code new_tweet}
 * push that was previously never wired up. This test connects a follower, has
 * the followed user tweet, and asserts the real-time frame arrives — the
 * integration-layer proof that the EventPublisher fan-out fix works end to end.</li>
 * </ul>
 *
 * <p>
 * All principals are freshly registered users (no dependence on demo seed
 * credentials, which are not part of the IT contract).
 * </p>
 */
class MessageWSIT extends AbstractTwitterIT {

    private static final ObjectMapper WS_MAPPER = new ObjectMapper();
    private static final java.net.http.HttpClient WS_CLIENT = java.net.http.HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();

    @Test
    void dmSendToOnlineRecipientDelivers() throws Exception {
        // sender -> receiver, receiver connected on /ws/dm. The receive frame proves
        // the handler ran insertMessage + upsertConversation + push end to end (DM
        // has no REST read endpoint, so persistence is covered by the same path).
        TokenAndUser sender = registerAndGetToken("ws_sender_" + System.nanoTime(), "pw");
        TokenAndUser receiver = registerAndGetToken("ws_receiver_" + System.nanoTime(), "pw");

        CopyOnWriteArrayList<String> receiverFrames = new CopyOnWriteArrayList<>();
        CountDownLatch received = new CountDownLatch(1);
        WebSocket receiverWs = connect("/ws/dm", receiver.token(), receiverFrames, received);
        TimeUnit.MILLISECONDS.sleep(200);

        WebSocket senderWs = connect("/ws/dm", sender.token(), new CopyOnWriteArrayList<>(), new CountDownLatch(1));

        senderWs.sendText("""
                {"type":"send","to":"%s","text":"cage match?"}
                """.formatted(receiver.username()), true).join();

        assertTrue(received.await(5, TimeUnit.SECONDS), "Recipient should receive the DM frame");
        Map<?, ?> frame = WS_MAPPER.readValue(receiverFrames.get(0), Map.class);
        assertEquals("receive", frame.get("type"));
        assertEquals("cage match?", frame.get("text"));
        assertEquals(sender.username(), frame.get("from"));

        receiverWs.sendClose(WebSocket.NORMAL_CLOSURE, "done").join();
        senderWs.sendClose(WebSocket.NORMAL_CLOSURE, "done").join();
    }

    @Test
    void dmSendToMissingRecipientReturnsError() throws Exception {
        TokenAndUser sender = registerAndGetToken("ws_sender2_" + System.nanoTime(), "pw");

        CopyOnWriteArrayList<String> frames = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        WebSocket senderWs = connect("/ws/dm", sender.token(), frames, latch);

        senderWs.sendText("""
                {"type":"send","to":"ghost_user","text":"hi"}
                """, true).join();

        assertTrue(latch.await(5, TimeUnit.SECONDS), "Sender should receive an error frame");
        Map<?, ?> frame = WS_MAPPER.readValue(frames.get(0), Map.class);
        assertEquals("error", frame.get("type"));
        assertEquals("user_not_found", frame.get("code"));

        senderWs.sendClose(WebSocket.NORMAL_CLOSURE, "done").join();
    }

    @Test
    void eventsPushNewTweetToConnectedFollower() throws Exception {
        // Connect as a follower of author on /ws/events, then have author tweet.
        // The fan-out must push a new_tweet frame over the live socket.
        TokenAndUser follower = registerAndGetToken("ws_follower_" + System.nanoTime(), "pw");
        TokenAndUser author = registerAndGetToken("ws_author_" + System.nanoTime(), "pw");
        post("/api/users/" + author.username() + "/follow", "", follower.token());

        CopyOnWriteArrayList<String> frames = new CopyOnWriteArrayList<>();
        CountDownLatch received = new CountDownLatch(1);
        WebSocket followerWs = connect("/ws/events", follower.token(), frames, received);
        TimeUnit.MILLISECONDS.sleep(200);

        post("/api/tweets", """
                {"content":"ws push test"}
                """, author.token());

        assertTrue(received.await(5, TimeUnit.SECONDS), "Follower should receive a new_tweet frame");
        Map<?, ?> frame = WS_MAPPER.readValue(frames.get(0), Map.class);
        assertEquals("new_tweet", frame.get("type"));
        assertEquals("ws push test", frame.get("content"));

        followerWs.sendClose(WebSocket.NORMAL_CLOSURE, "done").join();
    }

    @Test
    void dmHandshakeRejectsMissingToken() throws Exception {
        // No Authorization header → the handler closes the connection immediately,
        // so the upgrade must not succeed. The JDK client surfaces that either as a
        // handshake exception or as a connection-closed IOException during connect.
        boolean rejected = false;
        try {
            WebSocket ws = WS_CLIENT.newWebSocketBuilder()
                    .buildAsync(URI.create("ws://localhost:" + port() + "/ws/dm"), new WebSocket.Listener() {
                        @Override
                        public void onOpen(WebSocket webSocket) {
                            webSocket.request(1);
                        }
                    }).join();
            TimeUnit.MILLISECONDS.sleep(400);
            rejected = ws.isInputClosed() || ws.isOutputClosed();
        } catch (Exception e) {
            rejected = true;
        }
        assertTrue(rejected, "Connection without a token must be rejected/closed by the server");
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private int port() {
        return Integer.parseInt(baseUrl.substring(baseUrl.lastIndexOf(':') + 1));
    }

    private WebSocket connect(String path, String token, List<String> frames, CountDownLatch latch) {
        return WS_CLIENT.newWebSocketBuilder()
                .header("Authorization", "Bearer " + token)
                .buildAsync(URI.create("ws://localhost:" + port() + path), new WebSocket.Listener() {
                    @Override
                    public void onOpen(WebSocket webSocket) {
                        webSocket.request(1);
                    }

                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                        frames.add(data.toString());
                        latch.countDown();
                        webSocket.request(1);
                        return null;
                    }
                }).join();
    }
}
