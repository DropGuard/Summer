package summer.web;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import summer.web.exception.SummerWebException;

/**
 * Tests for {@link WebContext}.
 */
class WebContextTest {

	@Test
	void shouldCreateWebContext() {
		Request request = createRequest("GET", "/test");
		WebContext ctx = new WebContext(request);

		assertSame(request, ctx.request());
		assertNull(ctx.statusCode());
	}

	@Test
	void shouldGetPathFromRequest() {
		Request request = createRequest("GET", "/test/path");
		WebContext ctx = new WebContext(request);

		assertEquals("/test/path", ctx.path());
	}

	@Test
	void shouldGetMethodFromRequest() {
		Request request = createRequest("GET", "/test");
		WebContext ctx = new WebContext(request);

		assertEquals("GET", ctx.method());
	}

	@Test
	void shouldGetPathParam() {
		Request request = createRequest("GET", "/users/123");
		request.setAttribute("id", "123");
		WebContext ctx = new WebContext(request);

		assertEquals("123", ctx.pathParam("id"));
	}

	@Test
	void shouldGetQueryParam() {
		Request request = createRequestWithQuery("GET", "/test", "name=test");
		WebContext ctx = new WebContext(request);

		assertEquals("test", ctx.queryParam("name"));
	}

	@Test
	void shouldGetHeader() {
		Map<String, String> headers = new HashMap<>();
		headers.put("content-type", "application/json");
		Request request = createRequest("GET", "/test", headers);
		WebContext ctx = new WebContext(request);

		assertEquals("application/json", ctx.header("Content-Type"));
	}

	@Test
	void shouldSetStatusCode() {
		Request request = createRequest("GET", "/test");
		WebContext ctx = new WebContext(request);

		WebContext result = ctx.status(HttpStatus.CREATED);
		assertSame(ctx, result);
		assertEquals(HttpStatus.CREATED, ctx.statusCode());
	}

	@Test
	void shouldSetHeader() {
		Request request = createRequest("GET", "/test");
		WebContext ctx = new WebContext(request);

		WebContext result = ctx.setHeader("X-Custom", "value");
		assertSame(ctx, result);
		assertEquals("value", ctx.headers().get("X-Custom"));
	}

	@Test
	void shouldUseDefaultJsonConverterWhenNoneProvided() {
		Request request = createRequest("GET", "/test");
		WebContext ctx = new WebContext(request);

		// Should use default JsonBodyConverter
		ctx.json(HttpStatus.OK, "test");
		assertNotNull(ctx.converter());
		assertTrue(ctx.converter().supports("application/json"));
	}

	@Test
	void shouldUseInjectedConverter() {
		Request request = createRequest("GET", "/test");
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
		WebContext ctx = new WebContext(request, null, customConverter);

		ctx.json(HttpStatus.OK, "test");
		assertSame(customConverter, ctx.converter());
	}

	@Test
	void shouldThrowWhenBodyClassIsNotRecord() {
		Map<String, String> headers = new HashMap<>();
		headers.put("content-type", "application/json");
		Request request = createRequest("POST", "/test", headers);
		WebContext ctx = new WebContext(request);

		assertThrows(SummerWebException.class, () -> ctx.body(NotARecord.class));
	}

	@Test
	void shouldSendOkResponse() {
		Request request = createRequest("GET", "/test");
		WebContext ctx = new WebContext(request);

		ctx.ok("test result");

		assertEquals(HttpStatus.OK, ctx.statusCode());
	}

	@Test
	void shouldSendJsonResponse() {
		Request request = createRequest("GET", "/test");
		WebContext ctx = new WebContext(request);

		ctx.json(HttpStatus.NOT_FOUND, "Not Found");

		assertEquals(HttpStatus.NOT_FOUND, ctx.statusCode());
	}

	@Test
	void shouldSendErrorResponse() {
		Request request = createRequest("GET", "/test");
		WebContext ctx = new WebContext(request);

		Exception error = new RuntimeException("Test error");
		ctx.error(error);

		assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ctx.statusCode());
	}

	@Test
	void shouldSendWithCustomStatusCode() {
		Request request = createRequest("POST", "/test");
		WebContext ctx = new WebContext(request);

		ctx.json(HttpStatus.CREATED, "created");

		assertEquals(HttpStatus.CREATED, ctx.statusCode());
	}

	// Helper methods to create Request objects
	private Request createRequest(String method, String path) {
		return new Request(method, path, null, "application/json", new byte[0]);
	}

	private Request createRequest(String method, String path, Map<String, String> headers) {
		return new Request(method, path, null, "application/json", new byte[0], headers, path.getBytes());
	}

	private Request createRequestWithQuery(String method, String path, String query) {
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
