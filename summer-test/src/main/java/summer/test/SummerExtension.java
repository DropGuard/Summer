package summer.test;

import java.lang.reflect.Method;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestInstanceFactory;
import org.junit.jupiter.api.extension.TestInstanceFactoryContext;
import org.junit.jupiter.api.extension.TestInstantiationException;
import summer.core.BeanContainer;
import summer.core.Engine;
import summer.test.annotation.SummerTest;

/**
 * JUnit 5 extension for {@link SummerTest}.
 *
 * <p>
 * Builds a {@link BeanContainer} during test instance creation and resolves
 * constructor parameters against it — same injection contract as
 * {@code @Component}. The container scope is derived from the test class (its
 * module, widened by {@code modules}/{@code packages}), so the test sees
 * exactly the beans it should and nothing from unrelated tests.
 * </p>
 */
public class SummerExtension implements TestInstanceFactory, AfterAllCallback {

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
		BeanContainer container = TestContainerFactory.build(testClass, engine);
		extensionContext.getStore(NS).put(KEY, container);

		return TestContainerFactory.instantiate(testClass, container);
	}

	@Override
	public void afterAll(ExtensionContext ctx) throws Exception {
		BeanContainer container = getContext(ctx);
		if (container != null) {
			try {
				container.close();
			} catch (Exception ignored) {
			}
			ctx.getStore(NS).remove(KEY);
		}
	}

	private BeanContainer getContext(ExtensionContext ctx) {
		for (var current = ctx; current != null; current = current.getParent().orElse(null)) {
			BeanContainer c = current.getStore(NS).get(KEY, BeanContainer.class);
			if (c != null)
				return c;
		}
		return null;
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
