package summer.web.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import summer.web.Handler;
import summer.web.HttpMethod;
import summer.web.HttpStatus;
import summer.web.Middleware;
import summer.web.ServerConfig;
import summer.web.http.RadixTreeHttpRouter;
import summer.web.websocket.RadixWsRouter;

public class HttpMiddlewareIntegrationTest {

	private static NettyHttpServer server;
	private static int port;

	@BeforeAll
	public static void setup() throws Exception {
		RadixTreeHttpRouter httpRouter = new RadixTreeHttpRouter();
		RadixWsRouter wsRouter = new RadixWsRouter(List.of());

		Middleware testMiddleware = new Middleware() {
			@Override
			public Handler apply(Handler next) {
				return ctx -> {
					Object result = next.handle(ctx);
					ctx.setHeader("X-Test-Middleware", "Active");
					return result;
				};
			}
		};

		httpRouter.register(HttpMethod.GET, "/hello", ctx -> {
			ctx.text(HttpStatus.OK, "world");
			return null;
		});

		ServerConfig config = new ServerConfig(0, 30000, 1024 * 1024, 10000, List.of("*"), 65536);
		server = new NettyHttpServer(config, httpRouter, wsRouter, List.of(testMiddleware), null, null, List.of());

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
	public void testMiddlewareInterceptsAndModifiesResponse() throws Exception {
		HttpClient client = HttpClient.newHttpClient();
		HttpRequest request = HttpRequest.newBuilder().uri(URI.create("http://localhost:" + port + "/hello")).GET()
				.build();

		HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

		assertEquals(200, response.statusCode());
		assertEquals("world", response.body());

		List<String> middlewareHeader = response.headers().allValues("X-Test-Middleware");
		assertNotNull(middlewareHeader);
		assertEquals(1, middlewareHeader.size());
		assertEquals("Active", middlewareHeader.get(0));
	}
}
