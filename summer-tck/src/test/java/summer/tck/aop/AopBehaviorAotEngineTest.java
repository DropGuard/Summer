package summer.tck.aop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import summer.core.BeanContainer;
import summer.core.Engine;
import summer.fixtures.aop.ClassLevelGreeter;
import summer.fixtures.aop.Greeter;
import summer.fixtures.aop.GreeterService;
import summer.fixtures.aop.RecordingInterceptor;
import summer.test.Testing;
import summer.test.annotation.SummerTest;

/**
 * AOT-engine-specific pin for AOP interception behaviour.
 *
 * <p>
 * {@link AopBehaviorTest} already exercises the same fixtures through
 * {@code @DualEngine} (Runtime + AOT). This test adds an explicit,
 * engine-forced variant that builds the container with {@link Engine#AOT} only,
 * so the AOT proxy code path ({@code AotProxyGenerator}) is pinned on its own
 * rather than implicitly shared with the Runtime engine's {@code ProxyFactory}.
 * It guards against a regression where the two engines diverge on interceptor
 * binding resolution (class-level = all methods; method-level = that method
 * only), matching the Quarkus/CDI contract that bindings live on the
 * implementation class/method.
 * </p>
 */
@SummerTest
public class AopBehaviorAotEngineTest {

	@org.junit.jupiter.api.Test
	void aotEngineHonoursImplementationClassAndMethodBindings() {
		BeanContainer context = Testing.buildForTest(getClass(), Engine.AOT, List.of());

		// Method-level @Logged on GreeterService.greet must be intercepted.
		Greeter greeter = context.getBean(Greeter.class);
		assertEquals("[intercepted] Hello, Alice", greeter.greet("Alice"),
				"AOT: method-level @Logged must intercept greet()");

		// Non-annotated method must pass through untouched.
		assertEquals("HELLO", greeter.shout("hello"), "AOT: non-annotated method must return the raw result");

		// Returned bean must be a JDK proxy, not the raw impl.
		assertNotEquals(GreeterService.class, greeter.getClass(), "AOT: getBean(Greeter.class) must return a proxy");
		assertInstanceOf(Greeter.class, greeter);

		// Class-level @Logged on ClassLevelService must intercept EVERY method.
		ClassLevelGreeter classLevel = context.getBean(ClassLevelGreeter.class);
		assertEquals("[intercepted] Hello, Alice", classLevel.greet("Alice"),
				"AOT: class-level @Logged must intercept greet()");
		assertEquals("[intercepted] HELLO", classLevel.shout("hello"),
				"AOT: class-level @Logged must intercept shout() too");

		// Raw instance (concrete class) must NOT be proxied or intercepted.
		RecordingInterceptor interceptor = context.getBean(RecordingInterceptor.class);
		interceptor.clearLog();
		GreeterService raw = context.getBean(GreeterService.class);
		assertEquals(GreeterService.class, raw.getClass(), "AOT: getBean(concrete class) returns the raw instance");
		assertEquals("Hello, Charlie", raw.greet("Charlie"), "AOT: greet() on raw instance must NOT be intercepted");
		assertTrue(interceptor.getCallLog().isEmpty(), "AOT: interceptor must not fire on the raw instance");
	}
}
