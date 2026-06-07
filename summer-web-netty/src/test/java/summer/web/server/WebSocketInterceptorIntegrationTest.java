package summer.web.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import summer.web.Handler;
import summer.web.HttpStatus;
import summer.web.Middleware;
import summer.web.ServerConfig;
import summer.web.WsRouter;
import summer.web.http.RadixTreeHttpRouter;
import summer.web.websocket.RadixWsRouter;
import summer.web.websocket.WebSocketContext;
import summer.web.websocket.WsFilterChain;
import summer.web.websocket.WsInterceptor;

public class WebSocketInterceptorIntegrationTest {

	private static NettyHttpServer server;
	private static int port;

	@BeforeAll
	public static void setup() throws Exception {
		RadixTreeHttpRouter httpRouter = new RadixTreeHttpRouter();

		Middleware authMiddleware = new Middleware() {
			@Override
			public Handler apply(Handler next) {
				return ctx -> {
					String auth = ctx.header("X-Auth");
					if (!"Secret".equals(auth)) {
						ctx.status(HttpStatus.FORBIDDEN);
						ctx.text(HttpStatus.FORBIDDEN, "Unauthorized");
						return null;
					}
					return next.handle(ctx);
				};
			}
		};

		WsInterceptor testWsInterceptor = new WsInterceptor() {
			@Override
			public void intercept(WebSocketContext ctx, String message, WsFilterChain chain) {
				String modifiedMessage = "[INTERCEPTED] " + message;
				chain.doFilter(ctx, modifiedMessage);
			}
		};

		WsRouter wsRouter = new WsRouter.Builder(RadixWsRouter::new).ws("/ws-test", ctx -> {
			ctx.onMessage(msg -> {
				ctx.send(msg);
			});
		}).build();

		ServerConfig config = new ServerConfig(0, 30000, 1024 * 1024, 10000, List.of("*"), 65536);
		server = new NettyHttpServer(config, httpRouter, wsRouter, List.of(authMiddleware), null, null,
				List.of(testWsInterceptor));

		Thread serverThread = new Thread(() -> {
			server.start();
		});
		serverThread.start();

		Thread.sleep(1500);
		port = server.getPort();
	}

	@AfterAll
	public static void teardown() {
		if (server != null) {
			server.stop();
		}
	}

	@Test
	public void testHandshakeFailsWithoutAuthHeader() {
		HttpClient client = HttpClient.newHttpClient();
		String url = "ws://localhost:" + port + "/ws-test";

		assertThrows(java.util.concurrent.CompletionException.class, () -> {
			client.newWebSocketBuilder().buildAsync(URI.create(url), new WebSocket.Listener() {
			}).join();
		});
	}

	@Test
	public void testHandshakeSucceedsAndMessageIsIntercepted() throws Exception {
		HttpClient client = HttpClient.newHttpClient();
		String url = "ws://localhost:" + port + "/ws-test";

		CountDownLatch latch = new CountDownLatch(1);
		List<String> receivedMessages = new CopyOnWriteArrayList<>();

		WebSocket ws = client.newWebSocketBuilder().header("X-Auth", "Secret")
				.buildAsync(URI.create(url), new WebSocket.Listener() {
					@Override
					public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
						receivedMessages.add(data.toString());
						latch.countDown();
						return WebSocket.Listener.super.onText(webSocket, data, last);
					}
				}).join();

		ws.sendText("Hello World", true).join();

		boolean completed = latch.await(5, TimeUnit.SECONDS);
		assertTrue(completed, "Did not receive echoed message in time");

		assertEquals(1, receivedMessages.size());
		assertEquals("[INTERCEPTED] Hello World", receivedMessages.get(0));
	}
}
