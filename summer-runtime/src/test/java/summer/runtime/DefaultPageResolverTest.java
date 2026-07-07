package summer.runtime;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Parameter;
import org.junit.jupiter.api.Test;
import summer.core.config.PageableProperties;
import summer.web.*;

class DefaultPageResolverTest {

	private final DefaultPageResolver resolver = new DefaultPageResolver(new PageableProperties(0, 20));

	private HttpContext ctx(String query) {
		Request req = new Request(HttpMethod.GET, "/test", query, null, new byte[0]);
		return new HttpContext(req);
	}

	@Test
	void shouldResolveWithExplicitParams() throws Exception {
		HttpContext ctx = ctx("page=2&size=50");
		DefaultPageRequest pageable = (DefaultPageRequest) resolver.resolve(ctx, null);
		assertEquals(2, pageable.page());
		assertEquals(50, pageable.size());
	}

	@Test
	void shouldUseDefaultValuesWhenNoParams() throws Exception {
		HttpContext ctx = ctx(null);
		DefaultPageRequest pageable = (DefaultPageRequest) resolver.resolve(ctx, null);
		assertEquals(0, pageable.page());
		assertEquals(20, pageable.size());
	}

	@Test
	void shouldUseDefaultsForInvalidNumbers() throws Exception {
		HttpContext ctx = ctx("page=invalid&size=abc");
		DefaultPageRequest pageable = (DefaultPageRequest) resolver.resolve(ctx, null);
		assertEquals(0, pageable.page());
		assertEquals(20, pageable.size());
	}

	@Test
	void shouldClampNegativePageToZero() throws Exception {
		HttpContext ctx = ctx("page=-5");
		DefaultPageRequest pageable = (DefaultPageRequest) resolver.resolve(ctx, null);
		assertEquals(0, pageable.page());
	}

	@Test
	void shouldClampNegativeSizeToZero() throws Exception {
		HttpContext ctx = ctx("size=-10");
		DefaultPageRequest pageable = (DefaultPageRequest) resolver.resolve(ctx, null);
		assertEquals(0, pageable.size());
	}

	@Test
	void shouldUseCustomDefaults() throws Exception {
		DefaultPageResolver customResolver = new DefaultPageResolver(new PageableProperties(1, 10));
		HttpContext ctx = ctx(null);
		DefaultPageRequest pageable = (DefaultPageRequest) customResolver.resolve(ctx, null);
		assertEquals(1, pageable.page());
		assertEquals(10, pageable.size());
	}

	@Test
	void shouldSupportPageableParameter() throws Exception {
		assertTrue(resolver.supports(getParameter("withPageable")));
	}

	@Test
	void shouldNotSupportStringParameter() throws Exception {
		Parameter strParam = TestController.class.getDeclaredMethod("withString", String.class).getParameters()[0];
		assertFalse(resolver.supports(strParam));
	}

	// Helper to get Parameter reflection objects
	private Parameter getParameter(String methodName) throws Exception {
		return TestController.class.getDeclaredMethod(methodName, DefaultPageRequest.class).getParameters()[0];
	}

	// Test controller for parameter reflection
	static class TestController {
		public void withPageable(DefaultPageRequest pageable) {}
		public void withString(String str) {}
	}
}
