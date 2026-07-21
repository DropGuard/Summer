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
import summer.test.annotation.SummerTest;
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
	 * Builds the container for {@code testClass} on {@code engine}, applies any
	 * {@code @Mock} / {@code @TestProfile} metadata, and returns a freshly
	 * instantiated test instance — honouring the {@code shouldFail} contract so the
	 * caller (either the single-engine {@link SummerExtension} or the dual-engine
	 * {@code DualEngineInvocationProvider}) never re-encodes that policy.
	 *
	 * <p>
	 * {@code @SummerTest(shouldFail = true)} declares the assembly is expected to
	 * throw (Quarkus' {@code ArcTestContainer.shouldFail} model). The contract is
	 * enforced in both directions so a regression surfaces loudly on either side:
	 * </p>
	 * <ul>
	 * <li>{@code shouldFail = true} and the build throws → the test passes; no
	 * container exists and the (typically empty) test method runs against a
	 * {@code null} injection target.</li>
	 * <li>{@code shouldFail = true} but the build <em>succeeds</em> → the test
	 * <b>fails</b>: the framework promised assembly would reject this graph and it
	 * did not, which means the negative contract is broken.</li>
	 * <li>{@code shouldFail = false} (default) and the build throws → the test
	 * <b>fails</b>: a healthy graph must assemble.</li>
	 * </ul>
	 *
	 * <p>
	 * Because this is the single chokepoint, both engines are judged by the same
	 * rule — a divergence where one engine throws and the other silently wires a
	 * broken graph becomes a per-engine failure rather than a silent inconsistency.
	 * </p>
	 *
	 * @param testClass
	 *            the annotated test class
	 * @param engine
	 *            Runtime or AOT
	 * @return a new test instance wired from the built (or deliberately absent)
	 *         container
	 */
	/**
	 * Builds the container for {@code testClass} on {@code engine}, applies any
	 * {@code @Mock} / {@code @TestProfile} metadata, and returns a freshly
	 * instantiated test instance together with the built container — honouring the
	 * {@code shouldFail} contract so the caller (either the single-engine
	 * {@link SummerExtension} or the dual-engine
	 * {@code DualEngineInvocationProvider}) never re-encodes that policy. The
	 * container is returned alongside the instance so the lifecycle callback can
	 * close it without a thread-local or a second build.
	 *
	 * <p>
	 * {@code @SummerTest(shouldFail = true)} declares the assembly is expected to
	 * throw (Quarkus' {@code ArcTestContainer.shouldFail} model). The contract is
	 * enforced in both directions so a regression surfaces loudly on either side:
	 * </p>
	 * <ul>
	 * <li>{@code shouldFail = true} and the build throws → the test passes; no
	 * container exists and the (typically empty) test method runs against a
	 * {@code null} injection target.</li>
	 * <li>{@code shouldFail = true} but the build <em>succeeds</em> → the test
	 * <b>fails</b>: the framework promised assembly would reject this graph and it
	 * did not, which means the negative contract is broken.</li>
	 * <li>{@code shouldFail = false} (default) and the build throws → the test
	 * <b>fails</b>: a healthy graph must assemble.</li>
	 * </ul>
	 *
	 * <p>
	 * Because this is the single chokepoint, both engines are judged by the same
	 * rule — a divergence where one engine throws and the other silently wires a
	 * broken graph becomes a per-engine failure rather than a silent inconsistency.
	 * </p>
	 *
	 * @param testClass
	 *            the annotated test class
	 * @param engine
	 *            Runtime or AOT
	 * @return a {@link BuildOutcome} carrying the test instance and the container
	 *         (null when the build correctly failed) so the caller owns lifecycle
	 */
	public static BuildOutcome instantiateFor(Class<?> testClass, Engine engine) {
		boolean shouldFail = testClass.getAnnotation(SummerTest.class).shouldFail();

		List<MockedBean> mocks = createMocks(testClass);
		java.util.Map<String, Object> overrides = profileOverrides(testClass);

		BeanContainer container;
		try {
			container = Testing.buildForTest(testClass, engine, mocks, overrides);
		} catch (Exception buildFailure) {
			if (!shouldFail) {
				throw new TestInstantiationException(
						"@SummerTest container failed to assemble for " + testClass.getSimpleName() + " (engine="
								+ engine + "). Declare shouldFail=true if this is a negative test.",
						buildFailure);
			}
			// Expected failure path: assembly rejected the graph as the test promised.
			return new BuildOutcome(instantiateWithoutContainer(testClass), null);
		}

		// Build succeeded.
		if (shouldFail) {
			throw new TestInstantiationException("@SummerTest(shouldFail=true) on " + testClass.getSimpleName()
					+ " (engine=" + engine + ") expected assembly to fail, but the container built successfully. "
					+ "The negative contract is violated - the graph was accepted when it should have been rejected.");
		}
		return new BuildOutcome(instantiate(testClass, container), container);
	}

	/**
	 * The result of {@link #instantiateFor}: a test instance plus the container it
	 * was built from (or {@code null} when the build correctly failed under
	 * {@code shouldFail=true}). Returning both in one value lets the JUnit
	 * lifecycle callback close the container without a thread-local or a rebuild.
	 *
	 * @param instance
	 *            the freshly constructed test instance
	 * @param container
	 *            the built container, or {@code null} for an expected failure
	 */
	public record BuildOutcome(Object instance, BeanContainer container) {
	}

	/**
	 * Instantiates a {@code shouldFail} negative test whose container correctly
	 * failed to assemble. Such tests (missing dependency, circular dependency,
	 * self-injection) carry no bean dependencies, so the test instance is
	 * constructed with no arguments; its (typically empty) {@code @DualEngine} body
	 * then runs as the passing assertion.
	 *
	 * @throws TestInstantiationException
	 *             if the test class needs a constructor parameter but has no
	 *             container to satisfy it (a contradiction for a failed build)
	 */
	private static Object instantiateWithoutContainer(Class<?> testClass) {
		Constructor<?>[] ctors = testClass.getDeclaredConstructors();
		if (ctors.length != 1) {
			throw new TestInstantiationException("@SummerTest(shouldFail=true) class " + testClass.getName()
					+ " must have exactly one constructor. Found: " + ctors.length);
		}
		Constructor<?> ctor = ctors[0];
		ctor.setAccessible(true);
		for (Class<?> paramType : ctor.getParameterTypes()) {
			if (paramType != BeanContainer.class) {
				throw new TestInstantiationException("@SummerTest(shouldFail=true) class " + testClass.getSimpleName()
						+ " uses a constructor parameter of type " + paramType.getSimpleName()
						+ ", but a failed build provides no container to inject it from.");
			}
		}
		try {
			return ctor.newInstance();
		} catch (Exception e) {
			throw new TestInstantiationException("Failed to create @SummerTest instance: " + testClass.getName(), e);
		}
	}

	/**
	 * Builds a {@link BeanContainer} for the given test class and engine, applying
	 * any {@link TestProfile} overrides. Mocks declared via {@code @Mock} on the
	 * test constructor are assembled into {@link MockedBean}s first, so the real
	 * implementations are replaced at discovery stage. Retained for direct callers
	 * (e.g. integration tests that manage their own lifecycle); the standard
	 * {@link SummerExtension} / {@code DualEngineInvocationProvider} paths go
	 * through {@link #instantiateFor} so {@code shouldFail} is honoured uniformly.
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
