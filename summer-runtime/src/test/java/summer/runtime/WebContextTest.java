package summer.runtime;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import summer.web.HttpContext;
import summer.web.HttpMethod;
import summer.web.HttpStatus;
import summer.web.Request;

class WebContextTest {

	@Test
	void requestAccess() {
		Request req = new Request(HttpMethod.GET, "/test", "q=1", null, null);
		HttpContext ctx = new HttpContext(req);
		assertEquals(HttpMethod.GET, ctx.method());
		assertEquals("/test", ctx.path());
		assertEquals("1", ctx.queryParam("q"));
	}

	@Test
	void okJson() {
		Request req = new Request(HttpMethod.GET, "/test", null, null, null);
		HttpContext ctx = new HttpContext(req);
		ctx.ok("test-data");
		assertEquals(HttpStatus.OK, ctx.statusCode());
		assertNotNull(ctx.resultObject());
	}

	@Test
	void textResponse() {
		Request req = new Request(HttpMethod.GET, "/test", null, null, null);
		HttpContext ctx = new HttpContext(req);
		ctx.text(HttpStatus.OK, "hello");
		assertEquals(HttpStatus.OK, ctx.statusCode());
		assertArrayEquals("hello".getBytes(StandardCharsets.UTF_8), ctx.body());
	}

	@Test
	void errorResponse() {
		Request req = new Request(HttpMethod.GET, "/test", null, null, null);
		HttpContext ctx = new HttpContext(req);
		ctx.error(new RuntimeException("boom"));
		assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ctx.statusCode());
	}

	@Test
	void setHeader() {
		Request req = new Request(HttpMethod.GET, "/test", null, null, null);
		HttpContext ctx = new HttpContext(req);
		ctx.setHeader("X-Custom", "value");
		assertEquals("value", ctx.headers().get("X-Custom"));
	}
}
