package summer.runtime;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Parameter;
import org.junit.jupiter.api.Test;
import summer.core.config.PageableProperties;
import summer.web.CursorPageResolver;
import summer.web.CursorPageable;
import summer.web.HandlerParam;
import summer.web.HttpContext;
import summer.web.HttpMethod;
import summer.web.HttpParameterResolver;
import summer.web.Request;

class CursorPageResolverTest {

	private final CursorPageResolver resolver = new CursorPageResolver(new PageableProperties(0, 20));

	private HttpContext ctx(String query) {
		Request req = new Request(HttpMethod.GET, "/test", query, null, new byte[0]);
		return new HttpContext(req);
	}

	private HandlerParam cursorParam() throws Exception {
		Parameter param = TestController.class.getDeclaredMethod("withCursor", CursorPageable.class).getParameters()[0];
		return new RuntimeHandlerParam(param);
	}

	private HandlerParam stringParam() throws Exception {
		Parameter param = TestController.class.getDeclaredMethod("withString", String.class).getParameters()[0];
		return new RuntimeHandlerParam(param);
	}

	@Test
	void shouldResolveWithExplicitParams() throws Exception {
		HttpContext ctx = ctx("cursor=100&limit=50");
		CursorPageable pageable = (CursorPageable) resolver.resolve(ctx, cursorParam());
		assertEquals(100L, pageable.cursor());
		assertEquals(50, pageable.limit());
	}

	@Test
	void shouldDefaultLimitAndNullCursorWhenNoParams() throws Exception {
		HttpContext ctx = ctx(null);
		CursorPageable pageable = (CursorPageable) resolver.resolve(ctx, cursorParam());
		assertNull(pageable.cursor());
		assertEquals(20, pageable.limit());
	}

	@Test
	void shouldUseCustomDefaults() throws Exception {
		HttpParameterResolver customResolver = new CursorPageResolver(new PageableProperties(1, 10));
		HttpContext ctx = ctx(null);
		CursorPageable pageable = (CursorPageable) customResolver.resolve(ctx, cursorParam());
		assertNull(pageable.cursor());
		assertEquals(10, pageable.limit());
	}

	@Test
	void shouldClampNegativeLimitToZero() throws Exception {
		HttpContext ctx = ctx("limit=-10");
		CursorPageable pageable = (CursorPageable) resolver.resolve(ctx, cursorParam());
		assertEquals(0, pageable.limit());
	}

	@Test
	void shouldTreatNegativeCursorAsAbsent() throws Exception {
		HttpContext ctx = ctx("cursor=-5");
		CursorPageable pageable = (CursorPageable) resolver.resolve(ctx, cursorParam());
		assertNull(pageable.cursor());
	}

	@Test
	void shouldTreatUnparsableCursorAsAbsent() throws Exception {
		HttpContext ctx = ctx("cursor=not-a-number");
		CursorPageable pageable = (CursorPageable) resolver.resolve(ctx, cursorParam());
		assertNull(pageable.cursor());
	}

	@Test
	void shouldSupportCursorParameter() throws Exception {
		assertTrue(resolver.supports(cursorParam()));
	}

	@Test
	void shouldNotSupportStringParameter() throws Exception {
		assertFalse(resolver.supports(stringParam()));
	}

	// Test controller for parameter reflection
	static class TestController {
		public void withCursor(CursorPageable pageable) {
		}

		public void withString(String str) {
		}
	}
}
