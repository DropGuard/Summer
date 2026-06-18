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
import summer.core.BeanContainer;
import summer.core.annotation.Bean;
import summer.core.annotation.Configuration;
import summer.runtime.RuntimeApplicationContext;
import summer.web.*;
import summer.web.annotation.GlobalMiddleware;
import summer.web.websocket.WebSocketContext;
import summer.web.websocket.WsFilterChain;
import summer.web.websocket.WsInterceptor;

class WebSocketInterceptorIntegrationTest {

	@GlobalMiddleware
	public static class AuthMiddleware implements Middleware {
		@Override
		public Handler apply(Handler next) {
			return ctx -> {
				String auth = ctx.header("X-Auth");
				if (!"Secret".equals(auth)) {
					ctx.status(HttpStatus.FORBIDDEN);
					ctx.text(HttpStatus.FORBIDDEN, "Unauthorized");
					return;
				}
				next.handle(ctx);
			};
		}
	}

	@Configuration
	public static class TestConfig {
		public TestConfig() {
		}

		@Bean
		public WsInterceptor testWsInterceptor() {
			return new WsInterceptor() {
				@Override
				public void intercept(WebSocketContext ctx, String message, WsFilterChain chain) {
					String modifiedMessage = "[INTERCEPTED] " + message;
					chain.doFilter(ctx, modifiedMessage);
				}
			};
		}

		@Bean
		public WsRouteProvider wsRouteProvider() {
			return builder -> builder.ws("/ws-test", ctx -> {
				ctx.onMessage(msg -> {
					ctx.send(msg);
				});
			});
		}
	}

	private static BeanContainer context;
	private static NettyServerRunner serverRunner;

	private final String baseUrl = "ws://localhost:" + NettyServerRunner.getActualPort();

	@BeforeAll
	static void startServer() throws Exception {
		context = RuntimeApplicationContext.builder().registerComponent(AuthMiddleware.class)
				.registerComponent(TestConfig.class).build();
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
	void testHandshakeFailsWithoutAuthHeader() {
		HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

		assertThrows(java.util.concurrent.CompletionException.class, () -> {
			client.newWebSocketBuilder().buildAsync(URI.create(baseUrl + "/ws-test"), new WebSocket.Listener() {
			}).join();
		});
	}

	@Test
	void testHandshakeSucceedsAndMessageIsIntercepted() throws Exception {
		HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
		CountDownLatch latch = new CountDownLatch(1);
		List<String> receivedMessages = new CopyOnWriteArrayList<>();

		WebSocket ws = client.newWebSocketBuilder().header("X-Auth", "Secret")
				.buildAsync(URI.create(baseUrl + "/ws-test"), new WebSocket.Listener() {
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
