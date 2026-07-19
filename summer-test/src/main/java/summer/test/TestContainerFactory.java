package summer.test;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.extension.TestInstantiationException;
import summer.core.BeanContainer;
import summer.core.Engine;
import summer.core.bean.MockedBean;
import summer.test.annotation.Mock;
import summer.test.annotation.TestProfile;
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
 * A {@code @Mock} constructor parameter becomes a single {@link MockedBean}
 * (target type + Mockito instance), carried as one bound unit down the whole
 * build chain. The discovery stage removes the real bean of the target type so
 * it is never instantiated; the instance-build stage registers the mock. There
 * is no hand-rolled instance collection — mocks are declared, not assembled by
 * the caller.
 * </p>
 *
 * <p>
 * Profile overrides are resolved from the test class's {@code @TestProfile} and
 * passed explicitly down the build chain (as a {@code Map}) into both engines —
 * the Runtime engine consumes them via {@code ConfigBinder.BindingContext} at
 * binding time, while the AOT engine bakes them into the generated
 * {@code wire()} as the same {@code BindingContext} literal. There is no
 * thread-local and no {@code finally}-block cleanup, so a profile can never
 * leak into an unrelated test, even when the two engines run on different
 * virtual threads.
 * </p>
 */
public final class TestContainerFactory {

	private TestContainerFactory() {
	}

	/**
	 * Builds a {@link BeanContainer} for the given test class and engine, applying
	 * any {@link TestProfile} overrides. Mocks declared via {@code @Mock} on the
	 * test constructor are assembled into {@link MockedBean}s first, so the real
	 * implementations are replaced at discovery stage.
	 *
	 * @param testClass
	 *            the annotated test class
	 * @param engine
	 *            Runtime or AOT
	 * @return an immutable, fully wired container scoped to the test
	 */
	public static BeanContainer build(Class<?> testClass, Engine engine) {
		List<MockedBean> mocks = createMocks(testClass);
		java.util.Map<String, Object> overrides = profileOverrides(testClass);
		return Testing.buildForTest(testClass, engine, mocks, overrides);
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

	/**
	 * Assembles a {@link MockedBean} for every {@code @Mock} constructor parameter.
	 * The declared parameter type is the replacement target; the Mockito instance
	 * is created by {@link SummerExtension#createMock}.
	 */
	private static List<MockedBean> createMocks(Class<?> testClass) {
		List<MockedBean> mocks = new ArrayList<>();
		Constructor<?>[] ctors = testClass.getDeclaredConstructors();
		if (ctors.length != 1) {
			return mocks;
		}
		Annotation[][] paramAnnotations = ctors[0].getParameterAnnotations();
		Class<?>[] paramTypes = ctors[0].getParameterTypes();
		for (int i = 0; i < paramTypes.length; i++) {
			for (Annotation ann : paramAnnotations[i]) {
				if (ann instanceof Mock) {
					mocks.add(MockedBean.of(paramTypes[i], SummerExtension.createMock(paramTypes[i])));
				}
			}
		}
		return mocks;
	}

	/**
	 * Resolves the {@code @TestProfile} overrides for the test class. Returns an
	 * empty map when no profile is declared, so callers can thread the result
	 * straight into {@code ConfigBinder.BindingContext} without branching.
	 */
	private static java.util.Map<String, Object> profileOverrides(Class<?> testClass) {
		TestProfileSpec spec = resolveProfile(testClass);
		return spec != null ? spec.configOverrides() : java.util.Map.of();
	}

	private static TestProfileSpec resolveProfile(Class<?> testClass) {
		TestProfile ann = testClass.getAnnotation(TestProfile.class);
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
