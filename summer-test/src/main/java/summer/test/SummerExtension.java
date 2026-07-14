package summer.test;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestInstanceFactory;
import org.junit.jupiter.api.extension.TestInstanceFactoryContext;
import org.junit.jupiter.api.extension.TestInstantiationException;
import summer.core.BeanContainer;
import summer.test.annotation.Mock;
import summer.test.annotation.SummerTest;

/**
 * JUnit 5 extension for {@link SummerTest}.
 *
 * <p>
 * Builds a {@link BeanContainer} during test instance creation and resolves
 * constructor parameters against it — same injection contract as
 * {@code @Component}.
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

		// Single constructor — same contract as @Component
		Constructor<?>[] ctors = testClass.getDeclaredConstructors();
		if (ctors.length != 1) {
			throw new TestInstantiationException("@SummerTest class " + testClass.getName()
					+ " must have exactly one constructor. Found: " + ctors.length);
		}

		Constructor<?> ctor = ctors[0];
		ctor.setAccessible(true);

		// Detect @Mock parameters and create Mockito mocks
		List<Object> mocks = new ArrayList<>();
		Annotation[][] paramAnnotations = ctor.getParameterAnnotations();
		Class<?>[] paramTypes = ctor.getParameterTypes();
		for (int i = 0; i < paramTypes.length; i++) {
			for (Annotation ann : paramAnnotations[i]) {
				if (ann instanceof Mock) {
					mocks.add(createMock(paramTypes[i]));
				}
			}
		}

		// Module-scoped scan: auto-detect which module the test class belongs to
		BeanContainer container = Testing.buildForTest(testClass, mocks.toArray());
		extensionContext.getStore(NS).put(KEY, container);

		// Resolve constructor arguments from container
		Object[] args = new Object[paramTypes.length];
		for (int i = 0; i < paramTypes.length; i++) {
			if (paramTypes[i] == BeanContainer.class) {
				args[i] = container;
			} else {
				try {
					args[i] = container.getBean(paramTypes[i]);
				} catch (Exception e) {
					throw new TestInstantiationException("Cannot resolve constructor parameter "
							+ paramTypes[i].getSimpleName() + " for @SummerTest " + testClass.getSimpleName(), e);
				}
			}
		}

		try {
			return ctor.newInstance(args);
		} catch (Exception e) {
			throw new TestInstantiationException("Failed to create @SummerTest instance: " + testClass.getName(), e);
		}
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
	 * test classpath.
	 */
	private static Object createMock(Class<?> type) {
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
