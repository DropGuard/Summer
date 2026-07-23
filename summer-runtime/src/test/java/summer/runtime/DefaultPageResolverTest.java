package summer.runtime;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Parameter;
import org.junit.jupiter.api.Test;
import summer.core.config.PageableProperties;
import summer.web.DefaultPageRequest;
import summer.web.DefaultPageResolver;
import summer.web.HandlerParam;
import summer.web.HttpContext;
import summer.web.HttpMethod;
import summer.web.HttpParameterResolver;
import summer.web.Request;
import summer.web.ScrollRequest;

class DefaultPageResolverTest {

	private final DefaultPageResolver resolver = new DefaultPageResolver(new PageableProperties(0, 20));

	private HttpContext ctx(String query) {
		Request req = new Request(HttpMethod.GET, "/test", query, null, new byte[0]);
		return new HttpContext(req);
	}

	private HandlerParam pageableParam() throws Exception {
		Parameter param = TestController.class.getDeclaredMethod("withPageable", DefaultPageRequest.class)
				.getParameters()[0];
		return new RuntimeHandlerParam(param);
	}

	private HandlerParam stringParam() throws Exception {
		Parameter param = TestController.class.getDeclaredMethod("withString", String.class).getParameters()[0];
		return new RuntimeHandlerParam(param);
	}

	@Test
	void shouldResolveWithExplicitParams() throws Exception {
		HttpContext ctx = ctx("page=2&size=50");
		DefaultPageRequest pageable = (DefaultPageRequest) resolver.resolve(ctx, pageableParam());
		assertEquals(2, pageable.page());
		assertEquals(50, pageable.size());
	}

	@Test
	void shouldUseDefaultValuesWhenNoParams() throws Exception {
		HttpContext ctx = ctx(null);
		DefaultPageRequest pageable = (DefaultPageRequest) resolver.resolve(ctx, pageableParam());
		assertEquals(0, pageable.page());
		assertEquals(20, pageable.size());
	}

	@Test
	void shouldUseDefaultsForInvalidNumbers() throws Exception {
		HttpContext ctx = ctx("page=invalid&size=abc");
		DefaultPageRequest pageable = (DefaultPageRequest) resolver.resolve(ctx, pageableParam());
		assertEquals(0, pageable.page());
		assertEquals(20, pageable.size());
	}

	@Test
	void shouldClampNegativePageToZero() throws Exception {
		HttpContext ctx = ctx("page=-5");
		DefaultPageRequest pageable = (DefaultPageRequest) resolver.resolve(ctx, pageableParam());
		assertEquals(0, pageable.page());
	}

	@Test
	void shouldClampNegativeSizeToZero() throws Exception {
		HttpContext ctx = ctx("size=-10");
		DefaultPageRequest pageable = (DefaultPageRequest) resolver.resolve(ctx, pageableParam());
		assertEquals(0, pageable.size());
	}

	@Test
	void shouldUseCustomDefaults() throws Exception {
		HttpParameterResolver customResolver = new DefaultPageResolver(new PageableProperties(1, 10));
		HttpContext ctx = ctx(null);
		DefaultPageRequest pageable = (DefaultPageRequest) customResolver.resolve(ctx, pageableParam());
		assertEquals(1, pageable.page());
		assertEquals(10, pageable.size());
	}

	@Test
	void shouldSupportPageableParameter() throws Exception {
		assertTrue(resolver.supports(pageableParam()));
	}

	@Test
	void shouldNotSupportStringParameter() throws Exception {
		assertFalse(resolver.supports(stringParam()));
	}

	@Test
	void shouldNotClaimOtherScrollRequestSubtypes() throws Exception {
		// The resolver owns only DefaultPageRequest; other ScrollRequest subtypes
		// (cursor-based, limit/offset, ...) register their own resolver. Matching
		// on the broad ScrollRequest marker would let it wrongly claim them.
		Parameter param = TestController.class.getDeclaredMethod("withOtherScroll", OtherScrollRequest.class)
				.getParameters()[0];
		assertFalse(resolver.supports(new RuntimeHandlerParam(param)));
	}

	// Test controller for parameter reflection
	static class TestController {
		public void withPageable(DefaultPageRequest pageable) {
		}

		public void withString(String str) {
		}

		public void withOtherScroll(OtherScrollRequest other) {
		}
	}

	// Local ScrollRequest subtype that is NOT DefaultPageRequest, standing in for
	// cursor/limit-offset pageables defined in demo modules.
	record OtherScrollRequest(int x) implements ScrollRequest {
	}
}
