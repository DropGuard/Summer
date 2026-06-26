package summer.runtime;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Parameter;
import org.junit.jupiter.api.Test;
import summer.core.config.PageableProperties;
import summer.web.*;

/**
 * Unit tests for {@link PageableResolver}.
 *
 * <p>
 * Tests query parameter parsing, default values, and edge cases.
 * </p>
 */
class PageableResolverTest {

	private final PageableResolver resolver = new PageableResolver(new PageableProperties(0, 20));

	@Test
	void shouldResolveWithExplicitParams() throws Exception {
		Request request = new Request(HttpMethod.GET, "/articles", "page=2&size=10&sort=createdAt,desc", null, null);
		HttpContext ctx = new HttpContext(request);

		Pageable result = (Pageable) resolver.resolve(ctx, getParameter("pageableParam"));

		assertEquals(2, result.getPageNumber());
		assertEquals(10, result.getPageSize());
		assertTrue(result.getSort().isSorted());
		assertEquals("createdAt", result.getSort().orders().get(0).property());
		assertEquals(Sort.Direction.DESC, result.getSort().orders().get(0).direction());
	}

	@Test
	void shouldUseDefaultValuesWhenNoParams() throws Exception {
		Request request = new Request(HttpMethod.GET, "/articles", null, null, null);
		HttpContext ctx = new HttpContext(request);

		Pageable result = (Pageable) resolver.resolve(ctx, getParameter("pageableParam"));

		assertEquals(0, result.getPageNumber());
		assertEquals(20, result.getPageSize());
		assertFalse(result.getSort().isSorted());
	}

	@Test
	void shouldUseDefaultsForInvalidNumbers() throws Exception {
		Request request = new Request(HttpMethod.GET, "/articles", "page=abc&size=xyz", null, null);
		HttpContext ctx = new HttpContext(request);

		Pageable result = (Pageable) resolver.resolve(ctx, getParameter("pageableParam"));

		assertEquals(0, result.getPageNumber());
		assertEquals(20, result.getPageSize());
	}

	@Test
	void shouldClampNegativePageToZero() throws Exception {
		Request request = new Request(HttpMethod.GET, "/articles", "page=-5&size=10", null, null);
		HttpContext ctx = new HttpContext(request);

		Pageable result = (Pageable) resolver.resolve(ctx, getParameter("pageableParam"));

		assertEquals(0, result.getPageNumber());
		assertEquals(10, result.getPageSize());
	}

	@Test
	void shouldClampNegativeSizeToZero() throws Exception {
		Request request = new Request(HttpMethod.GET, "/articles", "page=0&size=-1", null, null);
		HttpContext ctx = new HttpContext(request);

		Pageable result = (Pageable) resolver.resolve(ctx, getParameter("pageableParam"));

		assertEquals(0, result.getPageNumber());
		assertEquals(0, result.getPageSize());
	}

	@Test
	void shouldParseAscendingSort() throws Exception {
		Request request = new Request(HttpMethod.GET, "/articles", "sort=name,asc", null, null);
		HttpContext ctx = new HttpContext(request);

		Pageable result = (Pageable) resolver.resolve(ctx, getParameter("pageableParam"));

		assertTrue(result.getSort().isSorted());
		assertEquals("name", result.getSort().orders().get(0).property());
		assertEquals(Sort.Direction.ASC, result.getSort().orders().get(0).direction());
	}

	@Test
	void shouldUseCustomDefaults() throws Exception {
		PageableResolver customResolver = new PageableResolver(new PageableProperties(1, 50));
		Request request = new Request(HttpMethod.GET, "/articles", null, null, null);
		HttpContext ctx = new HttpContext(request);

		Pageable result = (Pageable) customResolver.resolve(ctx, getParameter("pageableParam"));

		assertEquals(1, result.getPageNumber());
		assertEquals(50, result.getPageSize());
	}

	@Test
	void shouldSupportPageableParameter() throws Exception {
		Parameter param = TestController.class.getDeclaredMethod("pageableParam", Pageable.class).getParameters()[0];
		assertTrue(resolver.supports(param));
	}
	@Test
	void shouldNotSupportStringParameter() throws Exception {
		Parameter param = TestController.class.getDeclaredMethod("stringParam", String.class).getParameters()[0];
		assertFalse(resolver.supports(param));
	}
	// Helper to get Parameter reflection objects
	private Parameter getParameter(String methodName) throws Exception {
		return TestController.class.getDeclaredMethod(methodName, Pageable.class).getParameters()[0];
	}

	// Test controller for parameter reflection
	static class TestController {
		void pageableParam(Pageable pageable) {
		}
		void stringParam(String name) {
		}
	}
}
