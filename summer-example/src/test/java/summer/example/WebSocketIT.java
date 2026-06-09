package summer.example;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import summer.runtime.RuntimeApplicationContext;
import summer.test.annotation.SummerTest;
import summer.web.ServerConfig;

@SummerTest(value = RuntimeApplicationContext.class, web = true)
class WebSocketIT {

	@summer.core.annotation.Configuration
	@summer.core.annotation.Replaces(summer.data.redis.config.RedisAutoConfiguration.class)
	public static class MockRedisConfiguration {
		@summer.core.annotation.Bean
		public io.lettuce.core.api.sync.RedisCommands<String, Object> mockRedisCommands() {
			return org.mockito.Mockito.mock(io.lettuce.core.api.sync.RedisCommands.class);
		}
	}

	private final String baseUrl = "ws://localhost:" + ServerConfig.fromYaml().port();

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
