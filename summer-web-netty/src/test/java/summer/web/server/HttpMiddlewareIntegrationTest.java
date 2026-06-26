package summer.web.server;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import summer.core.BeanContainer;
import summer.fixtures.HttpTestController;
import summer.fixtures.HttpTestMiddleware;
import summer.runtime.RuntimeWebConfiguration;
import summer.test.TestContainerBuilder;

class HttpMiddlewareIntegrationTest {

	private static BeanContainer context;
	private static NettyServerRunner serverRunner;

	private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
	private final String baseUrl = "http://localhost:" + NettyServerRunner.getActualPort();

	@BeforeAll
	static void startServer() throws Exception {
		context = TestContainerBuilder.build(HttpTestMiddleware.class, HttpTestController.class,
				NettyServerConfiguration.class, RouterConfiguration.class, RuntimeWebConfiguration.class);
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
	void testMiddlewareInterceptsAndModifiesResponse() throws Exception {
		HttpResponse<String> response = client.send(
				HttpRequest.newBuilder().uri(URI.create(baseUrl + "/test/hello")).GET().build(),
				HttpResponse.BodyHandlers.ofString());

		assertEquals(200, response.statusCode());
		assertEquals("world", response.body());

		List<String> middlewareHeader = response.headers().allValues("X-Test-Middleware");
		assertNotNull(middlewareHeader);
		assertEquals(1, middlewareHeader.size());
		assertEquals("Active", middlewareHeader.get(0));
	}
}
