package summer.test.internal;
import java.lang.reflect.Method;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestInstanceFactory;
import org.junit.jupiter.api.extension.TestInstanceFactoryContext;
import org.junit.jupiter.api.extension.TestInstantiationException;
import summer.core.Engine;
import summer.core.Internal;
import summer.test.annotation.SummerTest;

/**
 * JUnit 5 extension for {@link SummerTest}.
 *
 * <p>
 * Builds a {@link BeanContainer} during test instance creation and resolves
 * constructor parameters against it — same injection contract as
 * {@code @Component}. The build (and the {@code shouldFail} contract) is
 * delegated to {@link SummerTestLifecycle#createUniverse}, the single owner of
 * the universe lifecycle, so both the single-engine and dual-engine paths judge
 * a negative test by the same rule.
 * </p>
 *
 * <p>
 * Container lifecycle is owned by {@link TestRunContext} (JVM-wide reuse,
 * closed on environment change / JVM exit) — this extension deliberately does
 * not close the container, matching the run-context's invariant that per-class
 * callbacks must not tear down a shared universe.
 * </p>
 */
@Internal
public class SummerExtension implements TestInstanceFactory {

	private static final ExtensionContext.Namespace NS = ExtensionContext.Namespace.create(SummerExtension.class);
	private static final String KEY = "BeanContainer";

	@Override
	public Object createTestInstance(TestInstanceFactoryContext factoryContext, ExtensionContext extensionContext)
			throws TestInstantiationException {
		Class<?> testClass = factoryContext.getTestClass();
		SummerTest summerTest = testClass.getAnnotation(SummerTest.class);
		if (summerTest == null) {
			return null;
		}

		// Engine selection is transparent to users; the dev switch (Summer:dev)
		// can force a specific engine, otherwise Runtime is the dev default.
		Engine engine = summerTest.engine();
		TestContainerFactory.BuildOutcome outcome = SummerTestLifecycle.createUniverse(testClass, engine);
		extensionContext.getStore(NS).put(KEY, outcome.container());

		return outcome.instance();
	}

	// ── Mockito bridge ──────────────────────────────────────────

	/**
	 * Creates a Mockito mock via reflection so summer-test does not hard-depend on
	 * Mockito at compile time. Requires {@code org.mockito:mockito-core} on the
	 * test classpath. Shared by all Summer test engines so mock handling behaves
	 * identically regardless of which DI engine builds the container.
	 */
	static Object createMock(Class<?> type) {
		try {
			Class<?> mockito = Class.forName("org.mockito.Mockito");
			Method mockMethod = mockito.getMethod("mock", Class.class);
			return mockMethod.invoke(null, type);
		} catch (ClassNotFoundException e) {
			throw new TestInstantiationException(
					"@Mock requires Mockito on the classpath. Add org.mockito:mockito-core as a test dependency.");
		} catch (Exception e) {
			throw new TestInstantiationException("Failed to create mock for " + type.getSimpleName(), e);
		}
	}
}
