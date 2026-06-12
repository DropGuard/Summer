package summer.web.server;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import summer.core.annotation.Bean;
import summer.core.annotation.Configuration;
import summer.runtime.RuntimeApplicationContext;
import summer.web.WsRouteProvider;

class WebSocketBroadcasterTest {

	@Configuration
	public static class TestConfig {
		public TestConfig() {
		}

		@Bean
		public NettyWebSocketBroadcaster broadcaster() {
			return new NettyWebSocketBroadcaster();
		}

		@Bean
		public WsRouteProvider wsRouteProvider(NettyWebSocketBroadcaster broadcaster) {
			return builder -> builder.ws("/chat/{room}", ctx -> {
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
			});
		}
	}

	private static RuntimeApplicationContext context;
	private static NettyServerRunner serverRunner;

	private final String baseUrl = "ws://localhost:" + NettyServerRunner.getActualPort();

	@BeforeAll
	static void startServer() throws Exception {
		context = new RuntimeApplicationContext();
		context.registerComponent(TestConfig.class);
		context.scan();
		context.initializeBeans();
		serverRunner = context.getBean(NettyServerRunner.class);
		serverRunner.run(context);
	}

	@AfterAll
	static void stopServer() throws Exception {
		if (serverRunner != null) {
			serverRunner.close();
		}
		if (context != null) {
			context.close();
		}
	}

	@Test
	void testWebSocketBroadcastingToRoom() throws Exception {
		HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
		CountDownLatch latch = new CountDownLatch(2);
		CopyOnWriteArrayList<String> client1Messages = new CopyOnWriteArrayList<>();
		CopyOnWriteArrayList<String> client2Messages = new CopyOnWriteArrayList<>();

		WebSocket ws1 = createClient(client, baseUrl + "/chat/room1", client1Messages, latch);
		TimeUnit.MILLISECONDS.sleep(200);

		WebSocket ws2 = createClient(client, baseUrl + "/chat/room1", client2Messages, latch);
		TimeUnit.MILLISECONDS.sleep(200);

		ws1.sendText("BROADCAST:Hello Room1", true).join();

		boolean received = latch.await(5, TimeUnit.SECONDS);
		assertTrue(received, "Did not receive broadcast within timeout");

		assertEquals(1, client1Messages.size());
		assertEquals("Hello Room1", client1Messages.get(0));
		assertEquals(1, client2Messages.size());
		assertEquals("Hello Room1", client2Messages.get(0));

		ws1.sendClose(WebSocket.NORMAL_CLOSURE, "Done").join();
		ws2.sendClose(WebSocket.NORMAL_CLOSURE, "Done").join();
	}

	private WebSocket createClient(HttpClient client, String url, List<String> messages, CountDownLatch latch) {
		return client.newWebSocketBuilder().buildAsync(URI.create(url), new WebSocket.Listener() {
			@Override
			public void onOpen(WebSocket webSocket) {
				webSocket.request(1);
			}

			@Override
			public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
				messages.add(data.toString());
				latch.countDown();
				webSocket.request(1);
				return null;
			}
		}).join();
	}
}
