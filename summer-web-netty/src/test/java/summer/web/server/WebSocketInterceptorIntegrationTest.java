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
import summer.fixtures.WsInterceptorTestConfig;
import summer.runtime.RuntimeWebConfiguration;
import summer.test.TestContainerBuilder;

class WebSocketInterceptorIntegrationTest {

	private static BeanContainer context;
	private static NettyServerRunner serverRunner;

	private final String baseUrl = "ws://localhost:" + NettyServerRunner.getActualPort();

	@BeforeAll
	static void startServer() throws Exception {
		context = TestContainerBuilder.buildRuntime(WsInterceptorTestConfig.class, NettyServerConfiguration.class,
				RouterConfiguration.class, RuntimeWebConfiguration.class);
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
