package summer.test;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.extension.TestInstantiationException;
import summer.core.BeanContainer;
import summer.core.Engine;
import summer.core.config.ConfigBinder;
import summer.test.annotation.Mock;
import summer.test.profile.TestProfileSpec;

/**
 * Shared test-container construction for every Summer test entry point.
 *
 * <p>
 * Both the single-engine {@link SummerExtension} and the dual-engine
 * {@code DualEngineInvocationProvider} build their containers through this
 * factory, so constructor injection, {@code @Mock} handling, and
 * {@code @TestProfile} override wiring behave identically regardless of which
 * DI engine (Runtime or AOT) is in use.
 * </p>
 *
 * <p>
 * Profile overrides are installed on the {@link ConfigBinder} thread-local
 * immediately before the container is built and cleared in a {@code finally}
 * block afterwards, so a profile can never leak into an unrelated test — even
 * when the two engines run on different virtual threads.
 * </p>
 */
public final class TestContainerFactory {

	private TestContainerFactory() {
	}

	/**
	 * Builds a {@link BeanContainer} for the given test class and engine, applying
	 * any {@link TestProfile} overrides. Mocks declared via {@code @Mock} on the
	 * test constructor are created first and registered as external beans so the
	 * real implementations are skipped.
	 *
	 * @param testClass
	 *            the annotated test class
	 * @param engine
	 *            Runtime or AOT
	 * @return an immutable, fully wired container scoped to the test
	 */
	public static BeanContainer build(Class<?> testClass, Engine engine) {
		List<Object> mocks = createMocks(testClass);
		TestProfileSpec profile = resolveProfile(testClass);
		try {
			if (profile != null) {
				ConfigBinder.setProfileOverrides(profile.configOverrides());
			}
			return Testing.buildForTest(testClass, engine, mocks.toArray());
		} finally {
			if (profile != null) {
				ConfigBinder.clearProfileOverrides();
			}
		}
	}

	/**
	 * Resolves the test class's single constructor against the container and
	 * instantiates it — the same injection contract as {@code @Component}.
	 *
	 * @throws TestInstantiationException
	 *             if the class has no single constructor or a parameter cannot be
	 *             resolved
	 */
	public static Object instantiate(Class<?> testClass, BeanContainer container) {
		Constructor<?>[] ctors = testClass.getDeclaredConstructors();
		if (ctors.length != 1) {
			throw new TestInstantiationException("@SummerTest class " + testClass.getName()
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

	private static List<Object> createMocks(Class<?> testClass) {
		List<Object> mocks = new ArrayList<>();
		Constructor<?>[] ctors = testClass.getDeclaredConstructors();
		if (ctors.length != 1) {
			return mocks;
		}
		Annotation[][] paramAnnotations = ctors[0].getParameterAnnotations();
		Class<?>[] paramTypes = ctors[0].getParameterTypes();
		for (int i = 0; i < paramTypes.length; i++) {
			for (Annotation ann : paramAnnotations[i]) {
				if (ann instanceof Mock) {
					mocks.add(SummerExtension.createMock(paramTypes[i]));
				}
			}
		}
		return mocks;
	}

	private static TestProfileSpec resolveProfile(Class<?> testClass) {
		summer.test.annotation.TestProfile ann = testClass.getAnnotation(summer.test.annotation.TestProfile.class);
		if (ann == null) {
			return null;
		}
		try {
			return ann.value().getDeclaredConstructor().newInstance();
		} catch (Exception e) {
			throw new TestInstantiationException(
					"Cannot instantiate @TestProfile " + ann.value().getName() + " (needs a no-arg constructor)", e);
		}
	}
}
