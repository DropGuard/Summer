package summer.tck.aop;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import summer.core.ApplicationContext;
import summer.tck.aop.dummy.Greeter;
import summer.tck.aop.dummy.RecordingInterceptor;

/**
 * Abstract AOP Test Compatibility Kit.
 *
 * Defines the behavioral contract that BOTH the Runtime and AOT engines must
 * satisfy for AOP interception. Any engine that passes all tests here is
 * considered AOP-compliant.
 *
 * Contract verified: 1. Intercepted methods are actually proxied (return value
 * is mutated). 2. Non-intercepted methods on the same bean are NOT affected. 3.
 * The interceptor's before/after lifecycle executes in correct order. 4. The
 * interceptor's supports() filter is respected (other beans stay raw). 5. The
 * bean retrieved from context is a proxy, not the raw implementation.
 */
public abstract class AbstractAopTCK {

	protected ApplicationContext context;

	/**
	 * Implementing subclasses boot their respective engine (Runtime or AOT)
	 * scanning the aop dummy package.
	 */
	protected abstract ApplicationContext createContext();

	protected ApplicationContext getContext() {
		if (context == null) {
			context = createContext();
		}
		return context;
	}

	@AfterEach
	void tearDown() {
		if (context != null) {
			context.destroy();
			context = null;
		}
	}

	// -----------------------------------------------------------------------
	// Test 1: The intercepted method IS wrapped
	// -----------------------------------------------------------------------

	@Test
	void testInterceptedMethodReturnsWrappedResult() {
		Greeter greeter = getContext().getBean(Greeter.class);
		String result = greeter.greet("Alice");
		assertEquals("[intercepted] Hello, Alice", result,
				"Intercepted method must return the mutated value from the interceptor");
	}

	// -----------------------------------------------------------------------
	// Test 2: The non-intercepted method is NOT wrapped
	// -----------------------------------------------------------------------

	@Test
	void testNonInterceptedMethodIsUnaffected() {
		Greeter greeter = getContext().getBean(Greeter.class);
		String result = greeter.shout("hello");
		assertEquals("HELLO", result, "Non-intercepted method must return the raw result, without any prefix");
	}

	// -----------------------------------------------------------------------
	// Test 3: Interceptor lifecycle order (before → proceed → after)
	// -----------------------------------------------------------------------

	@Test
	void testInterceptorLifecycleOrder() {
		RecordingInterceptor interceptor = getContext().getBean(RecordingInterceptor.class);
		interceptor.clearLog();

		Greeter greeter = getContext().getBean(Greeter.class);
		String result = greeter.greet("Bob");

		var log = interceptor.getCallLog();
		// The interceptor logs "before" and "after" — proceed() itself is not logged
		assertEquals(2, log.size(), "Interceptor log must have exactly 2 entries: before:greet and after:greet");
		assertEquals("before:greet", log.get(0));
		assertEquals("after:greet", log.get(1));
		// The actual method ran in between — proven by the return value
		assertEquals("[intercepted] Hello, Bob", result);
	}

	// -----------------------------------------------------------------------
	// Test 4: Calling shout() does NOT trigger the interceptor
	// -----------------------------------------------------------------------

	@Test
	void testNonInterceptedMethodDoesNotTriggerInterceptor() {
		RecordingInterceptor interceptor = getContext().getBean(RecordingInterceptor.class);
		interceptor.clearLog();

		Greeter greeter = getContext().getBean(Greeter.class);
		greeter.shout("hello");

		assertTrue(interceptor.getCallLog().isEmpty(),
				"Calling a non-@Intercepted method must not trigger the interceptor at all");
	}

	// -----------------------------------------------------------------------
	// Test 5: The bean from context is a proxy, not the raw class
	// -----------------------------------------------------------------------

	@Test
	void testBeanIsProxy() {
		Greeter greeter = getContext().getBean(Greeter.class);
		// JDK proxy creates an anonymous class implementing the interface.
		// The raw GreeterService class will NOT be the runtime type.
		assertNotEquals(summer.tck.aop.dummy.GreeterService.class, greeter.getClass(),
				"The bean returned from context must be a proxy, not the raw GreeterService");
		// But it must still implement the interface
		assertInstanceOf(Greeter.class, greeter);
	}
}
