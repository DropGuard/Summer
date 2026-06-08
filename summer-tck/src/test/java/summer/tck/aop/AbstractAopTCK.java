package summer.tck.aop;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import summer.fixtures.aop.ClassLevelGreeter;
import summer.fixtures.aop.Greeter;
import summer.fixtures.aop.RecordingInterceptor;
import summer.tck.AbstractContextTCK;

/**
 * Abstract AOP Test Compatibility Kit.
 *
 * <p>
 * Defines the behavioral contract that BOTH the Runtime and AOT engines must
 * satisfy for AOP interception. Any engine that passes all tests here is
 * considered AOP-compliant.
 */
public abstract class AbstractAopTCK extends AbstractContextTCK {

	// -----------------------------------------------------------------------
	// Intercepted method IS proxied
	// -----------------------------------------------------------------------

	@Test
	void testInterceptedMethodReturnsWrappedResult() {
		Greeter greeter = context().getBean(Greeter.class);
		String result = greeter.greet("Alice");
		assertEquals("[intercepted] Hello, Alice", result,
				"Intercepted method must return the mutated value from the interceptor");
	}

	// -----------------------------------------------------------------------
	// Non-intercepted method is NOT proxied
	// -----------------------------------------------------------------------

	@Test
	void testNonInterceptedMethodIsUnaffected() {
		Greeter greeter = context().getBean(Greeter.class);
		String result = greeter.shout("hello");
		assertEquals("HELLO", result, "Non-intercepted method must return the raw result, without any prefix");
	}

	// -----------------------------------------------------------------------
	// Interface lookup returns proxy
	// -----------------------------------------------------------------------

	@Test
	void testBeanIsProxy() {
		Greeter greeter = context().getBean(Greeter.class);
		assertNotEquals(summer.fixtures.aop.GreeterService.class, greeter.getClass(),
				"The bean returned from context must be a proxy, not the raw GreeterService");
		assertInstanceOf(Greeter.class, greeter);
	}

	// -----------------------------------------------------------------------
	// Concrete class lookup returns raw instance, bypassing interception
	// -----------------------------------------------------------------------

	@Test
	void testConcreteClassBypassesAop() {
		RecordingInterceptor interceptor = context().getBean(RecordingInterceptor.class);
		interceptor.clearLog();

		summer.fixtures.aop.GreeterService raw = context().getBean(summer.fixtures.aop.GreeterService.class);
		assertEquals(summer.fixtures.aop.GreeterService.class, raw.getClass(),
				"getBean(ConcreteClass.class) must return the raw instance, not a JDK proxy");

		String result = raw.greet("Charlie");
		assertEquals("Hello, Charlie", result, "greet() on raw instance must NOT be intercepted");
		assertTrue(interceptor.getCallLog().isEmpty(),
				"Interceptor must not fire when method is called on the raw instance");
	}

	// -----------------------------------------------------------------------
	// Class-level @Logged binding: ALL methods should be intercepted
	// -----------------------------------------------------------------------

	@Test
	void testClassLevelBindingInterceptsAllMethods() {
		ClassLevelGreeter greeter = context().getBean(ClassLevelGreeter.class);
		// Both greet() and shout() should be intercepted because @Logged is on the
		// class
		assertEquals("[intercepted] Hello, Alice", greeter.greet("Alice"),
				"Class-level @Logged must intercept greet()");
		assertEquals("[intercepted] HELLO", greeter.shout("hello"),
				"Class-level @Logged must intercept shout() even without method-level annotation");
	}
}
