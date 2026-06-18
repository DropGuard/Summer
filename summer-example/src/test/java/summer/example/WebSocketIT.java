package summer.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import summer.core.BeanContainer;
import summer.runtime.RuntimeApplicationContext;
import summer.web.server.NettyServerRunner;

class WebSocketIT {

	@summer.core.annotation.Configuration
	@summer.core.annotation.Replaces(summer.data.redis.config.RedisAutoConfiguration.class)
	public static class MockRedisConfiguration {
		@summer.core.annotation.Bean
		public io.lettuce.core.api.sync.RedisCommands<String, Object> mockRedisCommands() {
			return org.mockito.Mockito.mock(io.lettuce.core.api.sync.RedisCommands.class);
		}
	}

	private static BeanContainer context;
	private static NettyServerRunner serverRunner;

	private final String baseUrl = "ws://localhost:" + NettyServerRunner.getActualPort();

	@BeforeAll
	static void startServer() throws Exception {
		context = RuntimeApplicationContext.create();
		serverRunner = context.getBean(NettyServerRunner.class);
		serverRunner.run(context);
	}

	@AfterAll
	static void stopServer() throws Exception {
		if (serverRunner != null) serverRunner.close();
		if (context != null) context.close();
	}

	@Test
	void testWebSocketEcho() throws Exception {
		HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
		CountDownLatch latch = new CountDownLatch(1);
		AtomicReference<String> responseRef = new AtomicReference<>();

		WebSocket webSocket = client.newWebSocketBuilder()
				.buildAsync(URI.create(baseUrl + "/chat/room123"), new WebSocket.Listener() {
					@Override
					public void onOpen(WebSocket webSocket) {
						webSocket.request(1);
						webSocket.sendText("Hello Summer", true);
					}

					@Override
					public java.util.concurrent.CompletionStage<?> onText(WebSocket webSocket, CharSequence data,
							boolean last) {
						responseRef.set(data.toString());
						latch.countDown();
						return null;
					}
				}).join();

		boolean received = latch.await(5, TimeUnit.SECONDS);

		assertTrue(received, "Did not receive WebSocket response within timeout");
		assertEquals("Echo: Hello Summer", responseRef.get());

		webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Done").join();
	}
}
