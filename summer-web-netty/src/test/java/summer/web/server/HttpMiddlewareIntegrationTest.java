package summer.web.server;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import summer.runtime.RuntimeApplicationContext;
import summer.test.annotation.SummerTest;
import summer.web.*;
import summer.web.annotation.Get;
import summer.web.annotation.GlobalMiddleware;
import summer.web.annotation.RestController;

@SummerTest(value = RuntimeApplicationContext.class, web = true)
class HttpMiddlewareIntegrationTest {

	@GlobalMiddleware
	public static class TestMiddleware implements Middleware {
		@Override
		public Handler apply(Handler next) {
			return ctx -> {
				Object result = next.handle(ctx);
				ctx.setHeader("X-Test-Middleware", "Active");
				return result;
			};
		}
	}

	@RestController("/test")
	public static class TestController {
		@Get("/hello")
		public void hello(HttpContext ctx) {
			ctx.text(HttpStatus.OK, "world");
		}
	}

	private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
	private final String baseUrl = "http://localhost:" + ServerConfig.fromYaml().port();

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
