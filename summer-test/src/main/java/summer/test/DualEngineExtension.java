package summer.test;

import java.lang.reflect.Constructor;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;
import org.junit.jupiter.api.extension.TestInstanceFactory;
import org.junit.jupiter.api.extension.TestInstanceFactoryContext;
import org.junit.jupiter.api.extension.TestInstantiationException;
import summer.core.BeanContainer;
import summer.core.Engine;
import summer.test.annotation.DualEngineTest;

/**
 * JUnit 5 extension for {@link DualEngineTest}.
 *
 * <p>
 * Runs each {@code @Test} method twice — once per DI engine (Runtime + AOT).
 * </p>
 *
 * <p>
 * {@link #createTestInstance} creates the test instance wired with the Runtime
 * container. {@link #interceptTestMethod} calls {@code invocation.proceed()}
 * for the Runtime pass, then creates a fresh AOT-wired instance and invokes
 * the same test method via reflection.
 * </p>
 */
public class DualEngineExtension
		implements
			BeforeAllCallback,
			TestInstanceFactory,
			AfterAllCallback,
			InvocationInterceptor {

	private static final ExtensionContext.Namespace NS = ExtensionContext.Namespace.create(DualEngineExtension.class);
	private static final Engine[] ENGINES = Engine.values();

	@Override
	public void beforeAll(ExtensionContext ctx) throws Exception {
		Class<?> testClass = ctx.getRequiredTestClass();
		DualEngineTest ann = testClass.getAnnotation(DualEngineTest.class);
		if (ann == null)
			return;

		Class<?>[] seeds = ann.seeds();
		if (seeds.length == 0) {
			throw new IllegalArgumentException(
					"@DualEngineTest requires at least one seed class. "
					+ "Use @SummerTest or specify seeds explicitly.");
		}

		for (Engine engine : ENGINES) {
			BeanContainer container = Testing.build(engine, seeds);
			if (container == null) {
				throw new RuntimeException("[Summer] Failed to build container for engine: " + engine);
			}
			ctx.getStore(NS).put(engine.name(), container);
		}
	}

	@Override
	public Object createTestInstance(TestInstanceFactoryContext factoryContext, ExtensionContext context) {
		Class<?> testClass = factoryContext.getTestClass();

		// @Nested inner classes inherit from their enclosing @DualEngineTest class.
		// JUnit creates those instances itself — we just need to let them through.
		// They access the container via the enclosing test instance.
		Class<?> annotationSource = testClass;
		while (annotationSource != null && !annotationSource.isAnnotationPresent(DualEngineTest.class)) {
			annotationSource = annotationSource.getEnclosingClass();
		}
		if (annotationSource == null) {
			return null; // not a DualEngineTest — let JUnit handle it
		}
		if (annotationSource != testClass) {
			return null; // @Nested class — let JUnit use the enclosing instance
		}

		BeanContainer runtimeCtx = context.getStore(NS).get(Engine.RUNTIME.name(), BeanContainer.class);
		if (runtimeCtx == null) {
			throw new TestInstantiationException(
					"Runtime container not found for @DualEngineTest " + testClass.getName());
		}
		return createInstance(testClass, runtimeCtx);
	}

	@Override
	public void interceptTestMethod(Invocation<Void> invocation,
			ReflectiveInvocationContext<java.lang.reflect.Method> invocationContext, ExtensionContext context)
			throws Throwable {
		Class<?> testClass = context.getRequiredTestClass();
		// Run 1: Runtime via proceed() (satisfies JUnit interceptor chain)
		invocation.proceed();

		// Run 2: AOT via manual reflection
		BeanContainer aotCtx = context.getStore(NS).get(Engine.AOT.name(), BeanContainer.class);
		if (aotCtx != null) {
			Object aotInstance = createInstance(testClass, aotCtx);
			java.lang.reflect.Method method = invocationContext.getExecutable();
			try {
				method.invoke(aotInstance, invocationContext.getArguments().toArray());
			} catch (java.lang.reflect.InvocationTargetException e) {
				throw e.getCause();
			}
		}
	}

	@Override
	public void afterAll(ExtensionContext ctx) throws Exception {
		for (Engine engine : ENGINES) {
			BeanContainer container = ctx.getStore(NS).remove(engine.name(), BeanContainer.class);
			if (container != null) {
				try {
					container.close();
				} catch (Exception ignored) {
				}
			}
		}
	}

	// ── Helpers ─────────────────────────────────────────────────────

	private static Object createInstance(Class<?> testClass, BeanContainer container) {
		Constructor<?>[] ctors = testClass.getDeclaredConstructors();
		if (ctors.length != 1) {
			throw new TestInstantiationException("@DualEngineTest class " + testClass.getName()
					+ " must have exactly one constructor. Found: " + ctors.length);
		}

		Constructor<?> ctor = ctors[0];
		ctor.setAccessible(true);
		Class<?>[] paramTypes = ctor.getParameterTypes();
		Object[] args = new Object[paramTypes.length];

		for (int i = 0; i < paramTypes.length; i++) {
			if (paramTypes[i] == BeanContainer.class) {
				args[i] = container;
			} else {
				args[i] = container.getBean(paramTypes[i]);
			}
		}

		try {
			return ctor.newInstance(args);
		} catch (Exception e) {
			throw new TestInstantiationException("Failed to create @DualEngineTest instance: " + testClass.getName(),
					e);
		}
	}
}
