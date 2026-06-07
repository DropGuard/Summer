package summer.web.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import summer.web.ServerConfig;
import summer.web.WsRouter;
import summer.web.http.RadixTreeHttpRouter;
import summer.web.websocket.RadixWsRouter;

public class WebSocketBroadcasterTest {

	private static NettyHttpServer server;
	private static int port;
	private static NettyWebSocketBroadcaster broadcaster;

	@BeforeAll
	public static void setup() throws Exception {
		RadixTreeHttpRouter httpRouter = new RadixTreeHttpRouter();
		broadcaster = new NettyWebSocketBroadcaster();

		// 1. Setup route
		WsRouter wsRouter = new WsRouter.Builder(RadixWsRouter::new).ws("/chat/{room}", ctx -> {
			String room = ctx.pathParam("room");
			broadcaster.join(room, ctx);

			ctx.onMessage(msg -> {
				if (msg.startsWith("BROADCAST:")) {
					broadcaster.broadcast(room, msg.substring(10));
				}
			});

			ctx.onClose(() -> {
				broadcaster.leave(room, ctx);
			});
		}).build();

		// 2. Start server
		ServerConfig config = new ServerConfig(0, 30000, 1024 * 1024, 10000, List.of("*"), 65536);
		server = new NettyHttpServer(config, httpRouter, wsRouter, List.of(), null, null, List.of());

		Thread serverThread = new Thread(() -> {
			server.start();
		});
		serverThread.start();

		// Wait for server to bind
		Thread.sleep(1500); // Integration test: wait for server startup
		port = server.getPort();
	}

	@AfterAll
	public static void teardown() {
		if (server != null) {
			server.stop();
		}
	}

	@Test
	public void testWebSocketBroadcastingToRoom() throws Exception {
		HttpClient client = HttpClient.newHttpClient();
		String roomUrl = "ws://localhost:" + port + "/chat/tech";

		CountDownLatch latch = new CountDownLatch(3);
		List<String> receivedMessages1 = new CopyOnWriteArrayList<>();
		List<String> receivedMessages2 = new CopyOnWriteArrayList<>();
		List<String> receivedMessages3 = new CopyOnWriteArrayList<>();

		// Create 3 clients
		WebSocket ws1 = createClient(client, roomUrl, receivedMessages1, latch);
		WebSocket ws2 = createClient(client, roomUrl, receivedMessages2, latch);
		WebSocket ws3 = createClient(client, roomUrl, receivedMessages3, latch);

		// Wait for connection to be fully established and added to ChannelGroup
		Thread.sleep(500); // Integration test: wait for WebSocket handshake

		// Client 1 sends a broadcast message
		ws1.sendText("BROADCAST:Hello World", true).join();

		// Wait for all 3 clients to receive it
		boolean completed = latch.await(5, TimeUnit.SECONDS);

		assertTrue(completed, "Not all clients received the broadcast message in time");

		assertEquals(1, receivedMessages1.size());
		assertEquals("Hello World", receivedMessages1.get(0));

		assertEquals(1, receivedMessages2.size());
		assertEquals("Hello World", receivedMessages2.get(0));

		assertEquals(1, receivedMessages3.size());
		assertEquals("Hello World", receivedMessages3.get(0));
	}

	private WebSocket createClient(HttpClient client, String url, List<String> messages, CountDownLatch latch) {
		return client.newWebSocketBuilder().buildAsync(URI.create(url), new WebSocket.Listener() {
			@Override
			public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
				messages.add(data.toString());
				latch.countDown();
				return WebSocket.Listener.super.onText(webSocket, data, last);
			}
		}).join();
	}
}
