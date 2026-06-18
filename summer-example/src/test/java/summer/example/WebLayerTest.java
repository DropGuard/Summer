package summer.example;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import summer.core.BeanContainer;
import summer.runtime.RuntimeApplicationContext;
import summer.web.server.NettyServerRunner;

/**
 * Verifies the HTTP request -> middleware -> router -> controller -> service chain
 * through the framework's own Netty server.
 */
class WebLayerTest {

	@summer.core.annotation.Configuration
	@summer.core.annotation.Replaces(summer.data.redis.config.RedisAutoConfiguration.class)
	public static class MockRedisConfiguration {
		@summer.core.annotation.Bean
		public io.lettuce.core.api.sync.RedisCommands<String, Object> mockRedisCommands() {
			return org.mockito.Mockito.mock(io.lettuce.core.api.sync.RedisCommands.class);
		}

		@summer.core.annotation.Bean
		@summer.core.annotation.Replaces(summer.data.jdbc.RowMapperRegistry.class)
		public summer.data.jdbc.RowMapperRegistry rowMapperRegistry() {
			summer.data.jdbc.RowMapperRegistry registry = new summer.data.jdbc.RowMapperRegistry();
			registry.put(summer.example.User.class, (java.sql.ResultSet rs, int rowNum) -> new summer.example.User(
					rs.getString("id"),
					rs.getString("name"),
					rs.getString("email")));
			return registry;
		}
	}

	private static BeanContainer context;
	private static NettyServerRunner serverRunner;

	private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
	private final String baseUrl = "http://localhost:" + NettyServerRunner.getActualPort();

	@BeforeAll
	static void startServer() throws Exception {
		context = RuntimeApplicationContext.builder().registerComponent(MockRedisConfiguration.class).build();
		serverRunner = context.getBean(NettyServerRunner.class);
		serverRunner.run(context);
	}

	@AfterAll
	static void stopServer() throws Exception {
		if (serverRunner != null) serverRunner.close();
		if (context != null) context.close();
	}

	@Test
	void shouldRouteGetRequest() throws Exception {
		HttpResponse<String> resp = get("/users");
		assertEquals(200, resp.statusCode());
	}

	@Test
	void shouldSetCorsHeaders() throws Exception {
		HttpResponse<String> resp = get("/users");

		assertEquals("*", resp.headers().firstValue("Access-Control-Allow-Origin").orElse(null));
		assertNotNull(resp.headers().firstValue("Access-Control-Allow-Methods").orElse(null));
	}

	@Test
	void shouldHandlePreflightOptions() throws Exception {
		HttpResponse<String> resp = client.send(
				HttpRequest.newBuilder().uri(URI.create(baseUrl + "/users"))
						.method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build(),
				HttpResponse.BodyHandlers.ofString());

		assertEquals(204, resp.statusCode());
	}

	@Test
	void shouldReturn404ForUnknownRoute() throws Exception {
		HttpResponse<String> resp = get("/nonexistent");
		assertEquals(404, resp.statusCode());
	}

	@Test
	void shouldExtractPathParams() throws Exception {
		HttpResponse<String> createResp = post("/users", "{\"name\":\"Alice\",\"email\":\"alice@test.com\"}");
		assertEquals(201, createResp.statusCode());
		assertTrue(createResp.body().contains("Alice"));

		String body = createResp.body();
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"[iI]d\"\\s*:\\s*\"([^\"]+)\"").matcher(body);
		assertTrue(m.find(), "Response should contain id: " + body);
		String id = m.group(1);
		HttpResponse<String> getResp = get("/users/" + id);
		assertEquals(200, getResp.statusCode());
		assertTrue(getResp.body().contains("Alice"));
	}

	@Test
	void shouldHandlePostWithJsonBody() throws Exception {
		HttpResponse<String> resp = post("/users", "{\"name\":\"Bob\",\"email\":\"bob@test.com\"}");

		assertEquals(201, resp.statusCode());
		assertTrue(resp.body().contains("Bob"));
		assertTrue(resp.body().contains("bob@test.com"));
	}

	private HttpResponse<String> get(String path) throws Exception {
		return client.send(
				HttpRequest.newBuilder().uri(URI.create(baseUrl + path)).GET().build(),
				HttpResponse.BodyHandlers.ofString());
	}

	private HttpResponse<String> post(String path, String json) throws Exception {
		return client.send(
				HttpRequest.newBuilder().uri(URI.create(baseUrl + path))
						.header("Content-Type", "application/json")
						.POST(HttpRequest.BodyPublishers.ofString(json)).build(),
				HttpResponse.BodyHandlers.ofString());
	}
}
