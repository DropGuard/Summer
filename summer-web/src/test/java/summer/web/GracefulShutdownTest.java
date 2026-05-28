package summer.web;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import summer.core.annotation.Bean;
import summer.core.annotation.Configuration;
import summer.validation.BodyValidator;
import summer.validation.ValidationResult;
import summer.web.annotation.Get;
import summer.web.annotation.RestController;

public class GracefulShutdownTest {

	private static final int PORT = 8082;
	private static final CountDownLatch HOLD = new CountDownLatch(1);
	private static final CountDownLatch RELEASED = new CountDownLatch(20);

	@BeforeAll
	static void startServer() throws Exception {
		new Thread(() -> {
			try {
				SummerApplication.builder(ControllableApp.class).port(PORT).useRuntime().run(new String[0]);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}).start();
		Thread.sleep(2000);
	}

	@AfterAll
	static void stopServer() {
		HOLD.countDown(); // release any stuck requests
		SummerApplication.stop();
	}

	@Test
	void inFlightRequestsCompleteAndNewRequestsRejected() throws Exception {
		int requestCount = 20;
		ExecutorService executor = Executors.newFixedThreadPool(requestCount);
		HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

		// 1. Send requests that will block on HOLD
		AtomicInteger completed = new AtomicInteger(0);
		for (int i = 0; i < requestCount; i++) {
			executor.submit(() -> {
				try {
					HttpRequest request = HttpRequest.newBuilder().uri(URI.create("http://localhost:" + PORT + "/hold"))
							.timeout(Duration.ofSeconds(10)).GET().build();
					HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
					if (response.statusCode() == 200)
						completed.incrementAndGet();
				} catch (Exception ignored) {
				}
			});
		}

		// 2. Wait until requests are in-flight
		Thread.sleep(500);

		// 3. Trigger shutdown in background, wait for it to finish
		CountDownLatch shutdownDone = new CountDownLatch(1);
		new Thread(() -> {
			SummerApplication.stop();
			shutdownDone.countDown();
		}).start();

		// 4. Release the blocked requests
		Thread.sleep(200);
		HOLD.countDown();

		// 5. Wait for requests and shutdown to finish
		executor.shutdown();
		executor.awaitTermination(10, TimeUnit.SECONDS);
		shutdownDone.await(10, TimeUnit.SECONDS);

		// 6. Verify in-flight requests completed
		assertEquals(requestCount, completed.get(), "All in-flight requests should complete during shutdown");

		// 7. Verify new requests are rejected
		HttpRequest newRequest = HttpRequest.newBuilder().uri(URI.create("http://localhost:" + PORT + "/hold"))
				.timeout(Duration.ofSeconds(2)).GET().build();
		assertThrows(Exception.class, () -> client.send(newRequest, HttpResponse.BodyHandlers.ofString()),
				"New requests should be rejected after shutdown");
	}

	// --- Test fixtures ---

	public static class ControllableApp {
		public static void main(String[] args) throws Exception {
			SummerApplication.run(ControllableApp.class, args);
		}
	}

	@Configuration
	public static class TestConfig {
		@Bean
		public BodyValidator bodyValidator() {
			return new BodyValidator() {
				public ValidationResult validate(Object body) {
					return null;
				}
				public boolean supports(Class<?> type) {
					return false;
				}
			};
		}
	}

	@RestController
	public static class ControllableController {
		@Get("/hold")
		public String hold() throws InterruptedException {
			HOLD.await();
			return "done";
		}
	}
}
