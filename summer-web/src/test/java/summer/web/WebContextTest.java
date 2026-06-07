package summer.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import summer.web.exception.SummerWebException;

/**
 * Tests for {@link HttpContext}.
 */
class WebContextTest {

	@Test
	void shouldCreateWebContext() {
		Request request = createRequest(HttpMethod.GET, "/test");
		HttpContext ctx = new HttpContext(request);

		assertSame(request, ctx.request());
		assertNull(ctx.statusCode());
	}

	@Test
	void shouldGetPathFromRequest() {
		Request request = createRequest(HttpMethod.GET, "/test/path");
		HttpContext ctx = new HttpContext(request);

		assertEquals("/test/path", ctx.path());
	}

	@Test
	void shouldGetMethodFromRequest() {
		Request request = createRequest(HttpMethod.GET, "/test");
		HttpContext ctx = new HttpContext(request);

		assertEquals(HttpMethod.GET, ctx.method());
	}

	@Test
	void shouldGetPathParam() {
		Request request = createRequest(HttpMethod.GET, "/users/123");
		request.setAttribute("id", "123");
		HttpContext ctx = new HttpContext(request);

		assertEquals("123", ctx.pathParam("id"));
	}

	@Test
	void shouldGetQueryParam() {
		Request request = createRequestWithQuery(HttpMethod.GET, "/test", "name=test");
		HttpContext ctx = new HttpContext(request);

		assertEquals("test", ctx.queryParam("name"));
	}

	@Test
	void shouldGetHeader() {
		Map<String, String> headers = new HashMap<>();
		headers.put("content-type", "application/json");
		Request request = createRequest(HttpMethod.GET, "/test", headers);
		HttpContext ctx = new HttpContext(request);

		assertEquals("application/json", ctx.header("Content-Type"));
	}

	@Test
	void shouldSetStatusCode() {
		Request request = createRequest(HttpMethod.GET, "/test");
		HttpContext ctx = new HttpContext(request);

		HttpContext result = ctx.status(HttpStatus.CREATED);
		assertSame(ctx, result);
		assertEquals(HttpStatus.CREATED, ctx.statusCode());
	}

	@Test
	void shouldSetHeader() {
		Request request = createRequest(HttpMethod.GET, "/test");
		HttpContext ctx = new HttpContext(request);

		HttpContext result = ctx.setHeader("X-Custom", "value");
		assertSame(ctx, result);
		assertEquals("value", ctx.headers().get("X-Custom"));
	}

	@Test
	void shouldUseDefaultJsonConverterWhenNoneProvided() {
		Request request = createRequest(HttpMethod.GET, "/test");
		HttpContext ctx = new HttpContext(request);

		// Should use default JsonBodyConverter
		ctx.json(HttpStatus.OK, "test");
		assertNotNull(ctx.converter());
		assertTrue(ctx.converter().supports("application/json"));
	}

	@Test
	void shouldUseInjectedConverter() {
		Request request = createRequest(HttpMethod.GET, "/test");
		BodyConverter customConverter = new BodyConverter() {
			@Override
			public boolean supports(String contentType) {
				return "application/json".equals(contentType);
			}

			@Override
			public <T> T read(byte[] body, Class<T> type) {
				return null;
			}

			@Override
			public byte[] write(Object body) {
				return new byte[0];
			}

			@Override
			public String getContentType() {
				return "application/json";
			}
		};
		HttpContext ctx = new HttpContext(request, customConverter);

		ctx.json(HttpStatus.OK, "test");
		assertSame(customConverter, ctx.converter());
	}

	@Test
	void shouldThrowWhenBodyClassIsNotRecord() {
		Map<String, String> headers = new HashMap<>();
		headers.put("content-type", "application/json");
		Request request = createRequest(HttpMethod.POST, "/test", headers);
		HttpContext ctx = new HttpContext(request);

		assertThrows(SummerWebException.class, () -> ctx.body(NotARecord.class));
	}

	@Test
	void shouldSendOkResponse() {
		Request request = createRequest(HttpMethod.GET, "/test");
		HttpContext ctx = new HttpContext(request);

		ctx.ok("test result");

		assertEquals(HttpStatus.OK, ctx.statusCode());
	}

	@Test
	void shouldSendJsonResponse() {
		Request request = createRequest(HttpMethod.GET, "/test");
		HttpContext ctx = new HttpContext(request);

		ctx.json(HttpStatus.NOT_FOUND, "Not Found");

		assertEquals(HttpStatus.NOT_FOUND, ctx.statusCode());
	}

	@Test
	void shouldSendErrorResponse() {
		Request request = createRequest(HttpMethod.GET, "/test");
		HttpContext ctx = new HttpContext(request);

		Exception error = new RuntimeException("Test error");
		ctx.error(error);

		assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ctx.statusCode());
	}

	@Test
	void shouldSendWithCustomStatusCode() {
		Request request = createRequest(HttpMethod.POST, "/test");
		HttpContext ctx = new HttpContext(request);

		ctx.json(HttpStatus.CREATED, "created");

		assertEquals(HttpStatus.CREATED, ctx.statusCode());
	}

	// Helper methods to create Request objects
	private Request createRequest(HttpMethod method, String path) {
		return new Request(method, path, null, "application/json", new byte[0]);
	}

	private Request createRequest(HttpMethod method, String path, Map<String, String> headers) {
		return new Request(method, path, null, "application/json", new byte[0], headers, path.getBytes());
	}

	private Request createRequestWithQuery(HttpMethod method, String path, String query) {
		return new Request(method, path, query, "application/json", new byte[0]);
	}

	// Test helper class
	public static class NotARecord {
		private String name;

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}
	}
}
