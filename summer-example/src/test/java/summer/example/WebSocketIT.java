package summer.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import summer.boot.SummerApplication;

public class WebSocketIT {

	private static summer.core.ApplicationContext context;

	@BeforeAll
	public static void startServer() throws Exception {
		System.setProperty("summer.engine", "runtime");
		context = SummerApplication.run(Application.class, new String[0]);
		// Give it a tiny bit of time for Netty to bind
		Thread.sleep(1000);
	}

	@AfterAll
	public static void stopServer() throws Exception {
		if (context != null) {
			context.close();
		}
	}

	@Test
	public void testWebSocketEcho() throws Exception {
		HttpClient client = HttpClient.newHttpClient();
		CountDownLatch latch = new CountDownLatch(1);
		AtomicReference<String> responseRef = new AtomicReference<>();

		WebSocket webSocket = client.newWebSocketBuilder()
				.buildAsync(URI.create("ws://localhost:8080/chat/room123"), new WebSocket.Listener() {
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

		// Wait for response
		boolean received = latch.await(5, TimeUnit.SECONDS);

		// Assertions
		assertTrue(received, "Did not receive WebSocket response within timeout");
		assertEquals("Echo: Hello Summer", responseRef.get());

		webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Done").join();
	}
}
