package summer.runtime;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;
import summer.aop.SummerAopException;
import summer.web.*;

/**
 * Unit tests for {@link HandlerFactory}.
 *
 * <p>
 * Tests handler creation, parameter resolution, and exception handling.
 * </p>
 */
class HandlerFactoryTest {

	@Test
	void shouldCreateHandlerAndInvokeMethod() throws Exception {
		TestController controller = new TestController();
		Method method = TestController.class.getDeclaredMethod("hello", String.class);
		HttpParameterResolverChain chain = new HttpParameterResolverChain(List.of(new TestResolver("World")));

		Handler handler = HandlerFactory.create(controller, method, chain);
		Request request = new Request(HttpMethod.GET, "/hello", null, null, null);
		HttpContext ctx = new HttpContext(request);

		Object result = handler.handle(ctx);
		assertEquals("Hello, World", result);
	}

	@Test
	void shouldRethrowRuntimeExceptionDirectly() throws Exception {
		TestController controller = new TestController();
		Method method = TestController.class.getDeclaredMethod("throwRuntime");
		HttpParameterResolverChain chain = new HttpParameterResolverChain(List.of());

		Handler handler = HandlerFactory.create(controller, method, chain);
		Request request = new Request(HttpMethod.GET, "/error", null, null, null);
		HttpContext ctx = new HttpContext(request);

		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> handler.handle(ctx));
		assertEquals("bad argument", ex.getMessage());
	}

	@Test
	void shouldWrapCheckedExceptionInSummerAopException() throws Exception {
		TestController controller = new TestController();
		Method method = TestController.class.getDeclaredMethod("throwChecked");
		HttpParameterResolverChain chain = new HttpParameterResolverChain(List.of());

		Handler handler = HandlerFactory.create(controller, method, chain);
		Request request = new Request(HttpMethod.GET, "/error", null, null, null);
		HttpContext ctx = new HttpContext(request);

		SummerAopException ex = assertThrows(SummerAopException.class, () -> handler.handle(ctx));
		assertEquals("Handler invocation failed", ex.getMessage());
		assertInstanceOf(Exception.class, ex.getCause());
	}

	@Test
	void shouldResolveMultipleParameters() throws Exception {
		TestController controller = new TestController();
		Method method = TestController.class.getDeclaredMethod("greet", String.class, String.class);
		HttpParameterResolverChain chain = new HttpParameterResolverChain(
				List.of(new IndexedResolver(0, "Hello"), new IndexedResolver(1, "Alice")));

		Handler handler = HandlerFactory.create(controller, method, chain);
		Request request = new Request(HttpMethod.GET, "/greet", null, null, null);
		HttpContext ctx = new HttpContext(request);

		Object result = handler.handle(ctx);
		assertEquals("Hello, Alice", result);
	}

	// Test controller
	static class TestController {
		String hello(String name) {
			return "Hello, " + name;
		}

		String greet(String greeting, String name) {
			return greeting + ", " + name;
		}

		void throwRuntime() {
			throw new IllegalArgumentException("bad argument");
		}

		void throwChecked() throws Exception {
			throw new Exception("checked exception");
		}
	}

	// Test resolver that returns a fixed value
	static class TestResolver implements HttpParameterResolver {
		private final Object value;

		TestResolver(Object value) {
			this.value = value;
		}

		@Override
		public boolean supports(java.lang.reflect.Parameter parameter) {
			return true;
		}

		@Override
		public Object resolve(HttpContext ctx, java.lang.reflect.Parameter parameter) {
			return value;
		}
	}

	// Test resolver that returns value based on parameter index
	static class IndexedResolver implements HttpParameterResolver {
		private final int index;
		private final Object value;
		IndexedResolver(int index, Object value) {
			this.index = index;
			this.value = value;
		}
		@Override
		public boolean supports(java.lang.reflect.Parameter parameter) {
			return parameter.getDeclaringExecutable().getParameters()[index] == parameter;
		}
		@Override
		public Object resolve(HttpContext ctx, java.lang.reflect.Parameter parameter) {
			return value;
		}
	}
}
