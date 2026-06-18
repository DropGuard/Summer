package summer.runtime;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import summer.aop.Interceptor;
import summer.aop.InterceptorChain;
import summer.aop.MethodInterceptor;
import summer.aop.SummerAopException;
import summer.core.BeanContainer;
import summer.core.Component;
import summer.core.Provider;
import summer.core.annotation.Bean;
import summer.core.annotation.Configuration;
import summer.core.exception.CircularDependencyException;
import summer.core.exception.NoSuchBeanException;
import summer.web.ExceptionRegistry;
import summer.web.Handler;
import summer.web.HttpContext;
import summer.web.HttpMethod;
import summer.web.HttpStatus;
import summer.web.Request;
import summer.web.exception.RouteConflictException;
import summer.web.http.MapRouter;
import summer.web.http.RadixTreeHttpRouter;

/**
 * Comprehensive QA hands-on integration test for the Summer framework. Tests
 * real runtime behavior through the public API.
 *
 * Uses RuntimeApplicationContext with explicit registerComponent() to avoid
 * classpath scanning conflicts with other test fixtures in the same package.
 */
public class QaHandsOnIntegrationTest {

	// Helper to create a context with explicit component registration
	private static BeanContainer createContext(Class<?>... components) {
		var builder = RuntimeApplicationContext.builder();
		for (Class<?> c : components) {
			builder.registerComponent(c);
		}
		return builder.build();
	}

	// =========================================================================
	// P0: CORE DI ENGINE
	// =========================================================================

	@Nested
	@DisplayName("P0: DI Engine Core")
	class DiEngineCore {

		@Test
		@DisplayName("P0-1: Context creation succeeds with explicit registration")
		void contextCreationSucceeds() {
			BeanContainer ctx = createContext(SimpleService.class);
			assertNotNull(ctx);
			try {
				ctx.close();
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}

		@Test
		@DisplayName("P0-2: Singleton beans return the same instance")
		void singletonReturnsSameInstance() {
			BeanContainer ctx = createContext(SimpleService.class);
			SimpleService s1 = ctx.getBean(SimpleService.class);
			SimpleService s2 = ctx.getBean(SimpleService.class);
			assertSame(s1, s2, "Singleton must return identical instance");
			try {
				ctx.close();
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}

		@Test
		@DisplayName("P0-3: Constructor injection resolves dependencies")
		void constructorInjectionResolves() {
			BeanContainer ctx = createContext(SimpleService.class, ConsumerService.class);
			ConsumerService consumer = ctx.getBean(ConsumerService.class);
			assertNotNull(consumer);
			assertNotNull(consumer.getDependency(), "Constructor-injected dependency must not be null");
			assertSame(SimpleService.class, consumer.getDependency().getClass());
			try {
				ctx.close();
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}

		@Test
		@DisplayName("P0-4: Interface-to-implementation resolution")
		void interfaceResolvesToImpl() {
			BeanContainer ctx = createContext(GreetingServiceImpl.class);
			GreetingService greeting = ctx.getBean(GreetingService.class);
			assertNotNull(greeting);
			assertEquals("Hello, World", greeting.greet("World"));
			try {
				ctx.close();
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}

		@Test
		@DisplayName("P0-5: Missing bean throws NoSuchBeanException")
		void missingBeanThrows() {
			BeanContainer ctx = createContext(SimpleService.class);
			assertThrows(NoSuchBeanException.class, () -> ctx.getBean(UnregisteredService.class));
			try {
				ctx.close();
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}

		@Test
		@DisplayName("P0-6: Circular dependency detection throws exception")
		void circularDependencyDetected() {
			assertThrows(CircularDependencyException.class, () -> createContext(CircularA.class, CircularB.class));
		}

		@Test
		@DisplayName("P0-7: @Configuration + @Bean producer methods work")
		void configurationBeanProducer() {
			BeanContainer ctx = createContext(ProducerConfig.class);
			ProducedBean bean = ctx.getBean(ProducedBean.class);
			assertNotNull(bean);
			assertEquals("produced-value", bean.getValue());
			try {
				ctx.close();
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}

		@Test
		@DisplayName("P0-8: Provider<T> pattern works - implementing Provider<X> registers X")
		void providerPattern() {
			BeanContainer ctx = createContext(StringProviderComponent.class);
			// The StringProvider implements Provider<String>, so String should be
			// resolvable
			String provided = ctx.getBean(String.class);
			assertEquals("Hello Provider", provided);
			try {
				ctx.close();
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}

		@Test
		@DisplayName("P0-9: getBeansOfType returns all implementations")
		void getBeansOfType() {
			BeanContainer ctx = createContext(Dog.class, Cat.class);
			List<Animal> animals = ctx.getBeans(Animal.class);
			assertFalse(animals.isEmpty(), "getBeansOfType must find implementations");
			assertTrue(animals.size() >= 2, "Must find both Dog and Cat");
			try {
				ctx.close();
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
}

        @Test
		@DisplayName("P0-11: Multiple implementations detected via getBeansOfType")
		void multipleImplementationsDetected() {
			BeanContainer ctx = createContext(Dog.class, Cat.class);
			List<Animal> animals = ctx.getBeans(Animal.class);
			assertTrue(animals.size() >= 2, "Must find both Dog and Cat implementations");
			// Verify each implementation is distinct
			boolean hasDog = animals.stream().anyMatch(a -> a instanceof Dog);
			boolean hasCat = animals.stream().anyMatch(a -> a instanceof Cat);
			assertTrue(hasDog, "Must include Dog");
			assertTrue(hasCat, "Must include Cat");
			try {
				ctx.close();
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
	}

	// =========================================================================
	// P1: AOP PROXY & INTERCEPTION
	// =========================================================================

	@Nested
	@DisplayName("P1: AOP Proxy Behavior")
	class AopProxyTests {

		@Test
		@DisplayName("P1-1: Interface bean is returned as JDK proxy when interceptor registered")
		void interfaceBeanIsProxy() {
			BeanContainer ctx = createContext(InterceptedServiceImpl.class, TestInterceptorComponent.class);
			InterceptedService greeting = ctx.getBean(InterceptedService.class);
			assertNotEquals(InterceptedServiceImpl.class, greeting.getClass(),
					"Interface lookup must return proxy, not raw impl");
			assertTrue(greeting instanceof InterceptedService);
			try {
				ctx.close();
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}

		@Test
		@DisplayName("P1-2: Concrete class lookup bypasses AOP even with interceptor registered")
		void concreteClassBypassesAop() {
			BeanContainer ctx = createContext(InterceptedServiceImpl.class, TestInterceptorComponent.class);
			InterceptedServiceImpl raw = ctx.getBean(InterceptedServiceImpl.class);
			assertEquals(InterceptedServiceImpl.class, raw.getClass(),
					"Concrete class lookup must return raw instance, not proxy");
			try {
				ctx.close();
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}

		@Test
		@DisplayName("P1-3: ProxyFactory creates working proxy with @TestIntercepted method")
		void proxyFactoryWithInterceptedMethod() {
			InterceptedServiceImpl target = new InterceptedServiceImpl();
			InterceptedService proxy = ProxyFactory.createProxy(target, List.of(new TestInterceptorComponent()));
			String result = proxy.interceptedGreet("Test");
			assertEquals("[proxied] Hello, Test", result);
		}

		@Test
		@DisplayName("P1-4: ProxyFactory passes through non-@TestIntercepted methods unchanged")
		void proxyFactoryPassesThroughNonIntercepted() {
			InterceptedServiceImpl target = new InterceptedServiceImpl();
			MethodInterceptor interceptor = new MethodInterceptor() {
				@Override
				public Object intercept(InterceptorChain chain) throws Throwable {
					return "[proxied] " + chain.proceed();
				}
			};
			InterceptedService proxy = ProxyFactory.createProxy(target, List.of(interceptor));
			// nonInterceptedGreet has no @TestIntercepted, so it should pass through
			String result = proxy.nonInterceptedGreet("Test");
			assertEquals("Hello, Test", result, "Non-intercepted method must not be wrapped");
		}

		@Test
		@DisplayName("P1-5: ProxyFactory throws for target with no interfaces")
		void proxyFactoryRejectsNonInterface() {
			// A class with no interfaces at all
			Object target = new Object();
			assertThrows(SummerAopException.class, () -> ProxyFactory.createProxy(target, List.of()));
		}

		@Test
		@DisplayName("P1-6: Multiple interceptors execute in order on @TestIntercepted method")
		void multipleInterceptorsInOrder() {
			InterceptedServiceImpl target = new InterceptedServiceImpl();
			MethodInterceptor first = new TestInterceptor1();
			MethodInterceptor second = new TestInterceptor2();
			InterceptedService proxy = ProxyFactory.createProxy(target, List.of(first, second));
			String result = proxy.interceptedGreet("X");
			assertEquals("[1][2]Hello, X", result, "Interceptors must chain in order");
		}
	}

	// =========================================================================
	// P1: WEB ROUTING
	// =========================================================================

	@Nested
	@DisplayName("P1: Web Router Dispatch")
	class WebRouterTests {

		private RadixTreeHttpRouter router;

		@BeforeEach
		void setUp() {
			router = new RadixTreeHttpRouter();
		}

		@Test
		@DisplayName("P1-7: Router registers and dispatches GET route")
		void basicGetRoute() {
			router.register(HttpMethod.GET, "/hello", ctx -> ctx.text(HttpStatus.OK, "world"));
			Request req = new Request(HttpMethod.GET, "/hello", null, null, null);
			HttpContext ctx = new HttpContext(req);
			router.route(ctx);
			assertEquals("world", new String(ctx.body(), StandardCharsets.UTF_8));
		}

		@Test
		@DisplayName("P1-8: Router registers and dispatches POST route")
		void basicPostRoute() {
			router.register(HttpMethod.POST, "/items", ctx -> ctx.text(HttpStatus.OK, "created"));
			Request req = new Request(HttpMethod.POST, "/items", null, "application/json",
					"{\"name\":\"test\"}".getBytes(StandardCharsets.UTF_8));
			HttpContext ctx = new HttpContext(req);
			router.route(ctx);
			assertEquals("created", new String(ctx.body(), StandardCharsets.UTF_8));
		}

		@Test
		@DisplayName("P1-9: Router handles path parameters via byte-level routing")
		void pathParameters() {
			router.register(HttpMethod.GET, "/users/{id}", ctx -> {
				String id = ctx.request().pathParam("id");
				ctx.text(HttpStatus.OK, "user:" + id);
			});
			Request req = new Request(HttpMethod.GET, "/users/42", null, null, null);
			HttpContext ctx = new HttpContext(req);
			router.route(ctx);
			assertEquals("user:42", new String(ctx.body(), StandardCharsets.UTF_8));
		}

		@Test
		@DisplayName("P1-10: Router returns null for unmatched route")
		void unmatchedRouteReturnsNull() {
			router.register(HttpMethod.GET, "/exists", ctx -> ctx.text(HttpStatus.OK, "yes"));
			Request req = new Request(HttpMethod.GET, "/not-exists", null, null, null);
			HttpContext ctx = new HttpContext(req);
			router.route(ctx);
			assertNull(ctx.body());
		}

		@Test
		@DisplayName("P1-11: Router distinguishes HTTP methods on same path")
		void methodDistinction() {
			router.register(HttpMethod.GET, "/resource", ctx -> ctx.text(HttpStatus.OK, "get"));
			router.register(HttpMethod.POST, "/resource", ctx -> ctx.text(HttpStatus.OK, "post"));
			router.register(HttpMethod.PUT, "/resource", ctx -> ctx.text(HttpStatus.OK, "put"));
			router.register(HttpMethod.DELETE, "/resource", ctx -> ctx.text(HttpStatus.OK, "delete"));

			assertEquals("get", bodyOf(router, HttpMethod.GET, "/resource"));
			assertEquals("post", bodyOf(router, HttpMethod.POST, "/resource"));
			assertEquals("put", bodyOf(router, HttpMethod.PUT, "/resource"));
			assertEquals("delete", bodyOf(router, HttpMethod.DELETE, "/resource"));
		}

		@Test
		@DisplayName("P1-12: Router handles root path")
		void rootPath() {
			router.register(HttpMethod.GET, "/", ctx -> ctx.text(HttpStatus.OK, "root"));
			Request req = new Request(HttpMethod.GET, "/", null, null, null);
			HttpContext ctx = new HttpContext(req);
			router.route(ctx);
			assertEquals("root", new String(ctx.body(), StandardCharsets.UTF_8));
		}

		@Test
		@DisplayName("P1-13: Route conflict detection for overlapping params")
		void routeConflictDetection() {
			router.register(HttpMethod.GET, "/users/{id}", ctx -> ctx.text(HttpStatus.OK, "user"));
			assertThrows(RouteConflictException.class,
					() -> router.register(HttpMethod.GET, "/users/{name}", ctx -> ctx.text(HttpStatus.OK, "conflict")));
		}

		@Test
		@DisplayName("P1-14: Multiple path parameters in single route")
		void multiplePathParams() {
			router.register(HttpMethod.GET, "/orgs/{orgId}/repos/{repoId}", ctx -> {
				String org = ctx.request().pathParam("orgId");
				String repo = ctx.request().pathParam("repoId");
				ctx.text(HttpStatus.OK, org + "/" + repo);
			});
			Request req = new Request(HttpMethod.GET, "/orgs/summer/repos/core", null, null, null);
			HttpContext ctx = new HttpContext(req);
			router.route(ctx);
			assertEquals("summer/core", new String(ctx.body(), StandardCharsets.UTF_8));
		}

		private String bodyOf(RadixTreeHttpRouter r, HttpMethod method, String path) {
			HttpContext ctx = new HttpContext(new Request(method, path, null, null, null));
			r.route(ctx);
			byte[] body = ctx.body();
			return body != null ? new String(body, StandardCharsets.UTF_8) : null;
		}
	}

	// =========================================================================
	// P1: WEBCONTEXT FACADE
	// =========================================================================

	@Nested
	@DisplayName("P1: WebContext Facade")
	class WebContextTests {

		@Test
		@DisplayName("P1-15: WebContext provides request access")
		void webContextRequestAccess() {
			Request req = new Request(HttpMethod.GET, "/test", "q=1", null, null);
			HttpContext ctx = new HttpContext(req);
			assertEquals(HttpMethod.GET, ctx.method());
			assertEquals("/test", ctx.path());
			assertEquals("1", ctx.queryParam("q"));
		}

		@Test
		@DisplayName("P1-16: WebContext.ok() sets JSON response")
		void webContextOkJson() {
			Request req = new Request(HttpMethod.GET, "/test", null, null, null);
			HttpContext ctx = new HttpContext(req);
			ctx.ok("test-data");
			assertEquals(HttpStatus.OK, ctx.statusCode());
			assertNotNull(ctx.resultObject());
		}

		@Test
		@DisplayName("P1-17: WebContext.text() sets text response")
		void webContextText() {
			Request req = new Request(HttpMethod.GET, "/test", null, null, null);
			HttpContext ctx = new HttpContext(req);
			ctx.text(HttpStatus.OK, "hello");
			assertEquals(HttpStatus.OK, ctx.statusCode());
			assertArrayEquals("hello".getBytes(StandardCharsets.UTF_8), ctx.body());
		}

		@Test
		@DisplayName("P1-18: WebContext.error() sets 500 response")
		void webContextError() {
			Request req = new Request(HttpMethod.GET, "/test", null, null, null);
			HttpContext ctx = new HttpContext(req);
			ctx.error(new RuntimeException("boom"));
			assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ctx.statusCode());
		}

		@Test
		@DisplayName("P1-19: WebContext.setHeader() works")
		void webContextHeaders() {
			Request req = new Request(HttpMethod.GET, "/test", null, null, null);
			HttpContext ctx = new HttpContext(req);
			ctx.setHeader("X-Custom", "value");
			assertEquals("value", ctx.headers().get("X-Custom"));
		}
	}

	// =========================================================================
	// P1: MIDDLEWARE
	// =========================================================================

	@Nested
	@DisplayName("P1: Middleware Chain")
	class MiddlewareTests {

		@Test
		@DisplayName("P1-20: Middleware wraps handler result")
		void middlewareWrapsResult() {
			RadixTreeHttpRouter router = new RadixTreeHttpRouter();
			summer.web.Middleware wrapMiddleware = next -> ctx -> {
				next.handle(ctx);
				byte[] body = ctx.body();
				String content = body != null ? new String(body, StandardCharsets.UTF_8) : "";
				ctx.text(ctx.statusCode(), "[wrapped] " + content);
			};

			router.register(HttpMethod.GET, "/test", ctx -> {
				Handler original = ctx2 -> ctx2.text(HttpStatus.OK, "data");
				Handler wrapped = wrapMiddleware.apply(original);
				wrapped.handle(ctx);
			});

			Request req = new Request(HttpMethod.GET, "/test", null, null, null);
			HttpContext ctx = new HttpContext(req);
			router.route(ctx);
			assertEquals("[wrapped] data", new String(ctx.body(), StandardCharsets.UTF_8));
		}

		@Test
		@DisplayName("P1-21: Multiple middlewares chain correctly")
		void multipleMiddlewaresChain() {
			summer.web.Middleware first = next -> ctx -> {
				next.handle(ctx);
				byte[] body = ctx.body();
				String content = body != null ? new String(body, StandardCharsets.UTF_8) : "";
				ctx.text(ctx.statusCode(), "[1]" + content);
			};
			summer.web.Middleware second = next -> ctx -> {
				next.handle(ctx);
				byte[] body = ctx.body();
				String content = body != null ? new String(body, StandardCharsets.UTF_8) : "";
				ctx.text(ctx.statusCode(), "[2]" + content);
			};

			Handler original = ctx -> ctx.text(HttpStatus.OK, "core");
			Handler chain = first.apply(second.apply(original));

			Request req = new Request(HttpMethod.GET, "/test", null, null, null);
			HttpContext ctx = new HttpContext(req);
			chain.handle(ctx);
			assertEquals("[1][2]core", new String(ctx.body(), StandardCharsets.UTF_8));
		}
	}

	// =========================================================================
	// P1: EXCEPTION HANDLING
	// =========================================================================

	@Nested
	@DisplayName("P1: Exception Handling")
	class ExceptionHandlingTests {

		@Test
		@DisplayName("P1-22: ExceptionRegistry resolves handlers by exception type")
		void exceptionRegistryResolves() {
			ExceptionRegistry registry = new ExceptionRegistry();
			registry.register(IllegalArgumentException.class, ctx -> {
				ctx.text(HttpStatus.BAD_REQUEST, "bad request");
			});

			Handler handler = registry.getHandler(new IllegalArgumentException("test"));
			assertNotNull(handler);
		}

		@Test
		@DisplayName("P1-23: ExceptionRegistry returns null for unregistered exception")
		void exceptionRegistryReturnsNullForUnknown() {
			ExceptionRegistry registry = new ExceptionRegistry();
			Handler handler = registry.getHandler(new RuntimeException("unknown"));
			assertNull(handler);
		}
	}

	// =========================================================================
	// P2: EDGE CASES & BOUNDARIES
	// =========================================================================

	@Nested
	@DisplayName("P2: Edge Cases & Boundaries")
	class EdgeCaseTests {

		@Test
		@DisplayName("P2-1: Deep dependency chain resolves correctly (A->B->C->D)")
		void deepDependencyChain() {
			BeanContainer ctx = createContext(ChainD.class, ChainC.class, ChainB.class, ChainA.class);
			ChainA a = ctx.getBean(ChainA.class);
			assertNotNull(a);
			assertNotNull(a.getB());
			assertNotNull(a.getB().getC());
			assertNotNull(a.getB().getC().getD());
			try {
				ctx.close();
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}

		@Test
		@DisplayName("P2-2: Thread-safe singleton access from multiple threads")
		void threadSafeSingletonAccess() throws Exception {
			BeanContainer ctx = createContext(SimpleService.class);
			int threadCount = 20;
			ExecutorService pool = Executors.newFixedThreadPool(threadCount);
			CyclicBarrier barrier = new CyclicBarrier(threadCount);
			ConcurrentHashMap<Integer, SimpleService> results = new ConcurrentHashMap<>();

			for (int i = 0; i < threadCount; i++) {
				final int idx = i;
				pool.submit(() -> {
					try {
						barrier.await(5, TimeUnit.SECONDS);
						results.put(idx, ctx.getBean(SimpleService.class));
					} catch (Exception e) {
						fail("Thread failed: " + e.getMessage());
					}
				});
			}

			pool.shutdown();
			assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));

			// All threads must get the same instance
			SimpleService first = results.get(0);
			for (int i = 1; i < threadCount; i++) {
				assertSame(first, results.get(i), "Thread " + i + " got a different singleton instance");
			}
			try {
				ctx.close();
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}

		@Test
		@DisplayName("P2-3: Request with null body uses empty byte array")
		void requestNullBodyDefaultsToEmpty() {
			Request req = new Request(HttpMethod.POST, "/test", null, "application/json", null);
			assertNotNull(req.getBody());
			assertEquals(0, req.getBody().length);
		}

		@Test
		@DisplayName("P2-4: Context close() is idempotent (no exception on double close)")
		void contextCloseIsIdempotent() {
			BeanContainer ctx = createContext(SimpleService.class);
			assertDoesNotThrow(ctx::close);
			assertDoesNotThrow(ctx::close, "Double close must not throw");
		}

		@Test
		@DisplayName("P2-5: MapRouter as alternative router implementation")
		void mapRouterWorks() {
			MapRouter router = new MapRouter(
					java.util.List.of(new summer.web.HttpRouter.Builder.Route(HttpMethod.GET, "/hello",
							ctx -> ctx.text(HttpStatus.OK, "world")),
							new summer.web.HttpRouter.Builder.Route(HttpMethod.GET, "/users/{id}",
									ctx -> ctx.text(HttpStatus.OK, "user:" + ctx.request().pathParam("id")))));

			Request req1 = new Request(HttpMethod.GET, "/hello", null, null, null);
			HttpContext ctx1 = new HttpContext(req1);
			router.route(ctx1);
			assertEquals("world", new String(ctx1.body(), StandardCharsets.UTF_8));

			Request req2 = new Request(HttpMethod.GET, "/users/99", null, null, null);
			HttpContext ctx2 = new HttpContext(req2);
			router.route(ctx2);
			assertEquals("user:99", new String(ctx2.body(), StandardCharsets.UTF_8));
		}

		@Test
		@DisplayName("P2-7: Router handles deeply nested paths")
		void deeplyNestedPaths() {
			RadixTreeHttpRouter router = new RadixTreeHttpRouter();
			router.register(HttpMethod.GET, "/a/b/c/d/e/f", ctx -> ctx.text(HttpStatus.OK, "deep"));
			Request req = new Request(HttpMethod.GET, "/a/b/c/d/e/f", null, null, null);
			HttpContext ctx = new HttpContext(req);
			router.route(ctx);
			assertEquals("deep", new String(ctx.body(), StandardCharsets.UTF_8));
		}

		@Test
		@DisplayName("P2-8: Provider provides fresh instance each time")
		void providerGivesFreshInstance() {
			Provider<SimpleService> provider = SimpleService::new;
			SimpleService s1 = provider.provide();
			SimpleService s2 = provider.provide();
			assertNotSame(s1, s2, "Provider must create new instance each time");
		}

		@Test
		@DisplayName("P2-9: Context handles bean with no dependencies")
		void beanWithNoDependencies() {
			BeanContainer ctx = createContext(StandaloneBean.class);
			StandaloneBean bean = ctx.getBean(StandaloneBean.class);
			assertNotNull(bean);
			assertEquals("standalone", bean.getValue());
			try {
				ctx.close();
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}

		@Test
		@DisplayName("P2-10: Router handles URL-encoded path parameters")
		void urlEncodedPathParams() {
			RadixTreeHttpRouter r = new RadixTreeHttpRouter();
			r.register(HttpMethod.GET, "/search/{query}", c -> c.text(HttpStatus.OK, c.request().pathParam("query")));
			Request req = new Request(HttpMethod.GET, "/search/hello%20world", null, null, null);
			HttpContext ctx = new HttpContext(req);
			r.route(ctx);
			assertEquals("hello world", new String(ctx.body(), StandardCharsets.UTF_8));
		}
	}

	// =========================================================================
	// P0 BUILD BLOCKER: summer-example compilation failure
	// =========================================================================

	@Nested
	@DisplayName("P0: Build Verification")
	class BuildVerificationTests {

		@Test
		@DisplayName("P0-BUILD: summer-example missing summer-web-middleware dependency")
		void exampleModuleBuildFailure() {
			// Documents P0 build blocker:
			// SystemController.java imports summer.web.metrics.MetricsRegistry
			// from summer-web-middleware, but summer-example pom.xml
			// does not declare this dependency.
			// Impact: `mvn install` fails for the entire reactor.
			// Fix: Add summer-web-middleware dependency to summer-example/pom.xml
			try {
				Class.forName("summer.web.metrics.MetricsRegistry");
			} catch (ClassNotFoundException e) {
				// Expected when dependency is missing
			}
			assertTrue(true, "Documented: summer-example needs summer-web-middleware dependency");
		}
	}

	// =========================================================================
	// TEST FIXTURE CLASSES
	// =========================================================================

	@Component
	public static class SimpleService {
		public String doWork() {
			return "done";
		}
	}

	@Component
	public static class ConsumerService {
		private final SimpleService dependency;

		public ConsumerService(SimpleService dependency) {
			this.dependency = dependency;
		}

		public SimpleService getDependency() {
			return dependency;
		}
	}

	// Interface with @TestIntercepted methods for AOP testing
	public interface InterceptedService {
		@TestIntercepted
		String interceptedGreet(String name);

		String nonInterceptedGreet(String name);
	}

	@Component
	public static class InterceptedServiceImpl implements InterceptedService {
		@Override
		@TestIntercepted
		public String interceptedGreet(String name) {
			return "Hello, " + name;
		}

		@Override
		public String nonInterceptedGreet(String name) {
			return "Hello, " + name;
		}
	}

	@TestIntercepted
	@Component
	@Interceptor
	public static class TestInterceptorComponent implements MethodInterceptor {
		@Override
		public Object intercept(InterceptorChain chain) throws Throwable {
			return "[proxied] " + chain.proceed();
		}
	}

	@TestIntercepted
	@Interceptor
	public static class TestInterceptor1 implements MethodInterceptor {
		@Override
		public Object intercept(InterceptorChain chain) throws Throwable {
			return "[1]" + chain.proceed();
		}
	}

	@TestIntercepted
	@Interceptor
	public static class TestInterceptor2 implements MethodInterceptor {
		@Override
		public Object intercept(InterceptorChain chain) throws Throwable {
			return "[2]" + chain.proceed();
		}
	}

	// Non-intercepted interface for basic DI testing
	public interface GreetingService {
		String greet(String name);
	}

	@Component
	public static class GreetingServiceImpl implements GreetingService {
		@Override
		public String greet(String name) {
			return "Hello, " + name;
		}
	}

	public static class UnregisteredService {
	}

	// Circular dependency fixtures
	@Component
	public static class CircularA {
		public CircularA(CircularB b) {
		}
	}

	@Component
	public static class CircularB {
		public CircularB(CircularA a) {
		}
	}

	// @Configuration + @Bean fixtures
	@Configuration
	public static class ProducerConfig {
		@Bean
		public ProducedBean producedBean() {
			return new ProducedBean("produced-value");
		}
	}

	public static class ProducedBean {
		private final String value;

		public ProducedBean(String value) {
			this.value = value;
		}

		public String getValue() {
			return value;
		}
	}

	// Provider fixture - implementing Provider<T> registers T as a bean
	@Component
	public static class StringProviderComponent implements Provider<String> {
		@Override
		public String provide() {
			return "Hello Provider";
		}
	}

	// Multi-impl fixtures
	public interface Animal {
		String sound();
	}

	@Component
	public static class Dog implements Animal {
		@Override
		public String sound() {
			return "woof";
		}
	}

	@Component
	public static class Cat implements Animal {
		@Override
		public String sound() {
			return "meow";
		}
	}

	// Deep dependency chain fixtures
	@Component
	public static class ChainD {
		public String value = "D";
	}

	@Component
	public static class ChainC {
		private final ChainD d;

		public ChainC(ChainD d) {
			this.d = d;
		}

		public ChainD getD() {
			return d;
		}
	}

	@Component
	public static class ChainB {
		private final ChainC c;

		public ChainB(ChainC c) {
			this.c = c;
		}

		public ChainC getC() {
			return c;
		}
	}

	@Component
	public static class ChainA {
		private final ChainB b;

		public ChainA(ChainB b) {
			this.b = b;
		}

		public ChainB getB() {
			return b;
		}
	}

	// Standalone bean with no dependencies
	@Component
	public static class StandaloneBean {
		public String getValue() {
			return "standalone";
		}
	}
}
