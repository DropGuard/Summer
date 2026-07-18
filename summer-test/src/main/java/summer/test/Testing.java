package summer.test;

import java.util.List;
import org.jboss.jandex.IndexView;
import summer.core.BeanContainer;
import summer.core.Component;
import summer.core.Engine;
import summer.core.bean.MockedBean;
import summer.core.bean.ModuleIndex;
import summer.core.bean.Scope;
import summer.runtime.JandexIndexLoader;
import summer.runtime.RuntimeBeanContainerBuilder;

/**
 * Unified test container builder for Summer's DI engines.
 *
 * <p>
 * A {@code @SummerTest} container always spans the <b>test universe</b>: the
 * whole application (every production bean across all modules) plus whatever
 * test beans are on the classpath — controllers, stub configs, route fixtures.
 * This mirrors Quarkus' {@code @QuarkusTest}: there is no seed list, no module
 * narrowing, no fixture switch. A test bean is discovered exactly like a
 * production bean — if it is indexed and in scope, the container wires it.
 * Isolation between tests comes from {@code @TestProfile} (configuration
 * variants) and {@code @Mock} (bean stubs), never from shrinking the discovery
 * universe. Mocks are declared with {@code @Mock} on a test constructor
 * parameter and supplied by the framework — there is no hand-rolled instance
 * collection to register (a concept Quarkus does not expose).
 * </p>
 *
 * <p>
 * Both DI engines observe this identical universe, which is the foundation of
 * framework-enforced dual-engine consistency: a test verified on Runtime and
 * AOT sees the same candidate set.
 * </p>
 */
public final class Testing {

	private Testing() {
	}

	// ── User path: engine transparent ───────────────────────────────────

	/**
	 * Builds a container over the full test universe using the Runtime engine (dev
	 * mode default). Equivalent to a {@code @SummerTest} with no extra
	 * configuration.
	 */
	public static BeanContainer build() {
		return buildForTest(null);
	}

	/**
	 * Auto-scans a package tree for components. Retained for one-off package scoped
	 * builds; the test-universe default is preferred for {@code @SummerTest}.
	 */
	public static BeanContainer scan(String basePackage) {
		return RuntimeBeanContainerBuilder.buildFromPackage(basePackage);
	}

	// ── TCK path: explicit engine, identical universe ───────────────────

	/**
	 * Builds a container scoped to a {@code @SummerTest} class, on the requested
	 * engine.
	 *
	 * <p>
	 * The universe is always the test universe (application + test beans), shared
	 * verbatim by both engines — the precondition for dual-engine consistency.
	 * Mocks are supplied as {@link MockedBean}s (one bound struct per {@code @Mock}
	 * parameter, carrying the target type and its Mockito instance); the discovery
	 * stage removes the real bean of each target type so it is never instantiated.
	 * There is no hand-built instance collection — mocks are declared, not
	 * assembled by the caller (a concept Quarkus does not expose).
	 * </p>
	 *
	 * @param testClass
	 *            the annotated test class
	 * @param engine
	 *            Runtime or AOT
	 * @param mocks
	 *            mocked beans produced from {@code @Mock} parameters (internal —
	 *            not a public registration API)
	 * @return immutable bean container
	 */
	public static BeanContainer buildForTest(Class<?> testClass, Engine engine, List<MockedBean> mocks) {
		Scope scope = testUniverseScope();
		if (engine == Engine.AOT) {
			return buildAot(AotKey.forTest(testClass), scope, mocks);
		}
		return RuntimeBeanContainerBuilder.build(scope, mocks);
	}

	/**
	 * Builds a container for a {@code @SummerTest} class using the dev-mode default
	 * engine (Runtime). See {@link #buildForTest(Class, Engine, List)}.
	 */
	public static BeanContainer buildForTest(Class<?> testClass) {
		return buildForTest(testClass, Engine.RUNTIME, List.of());
	}

	// ── Internals ─────────────────────────────────────────────────────

	/**
	 * The test universe scope: every class in the test index (production beans plus
	 * test-class beans). Shared by Runtime and AOT so both engines see the same
	 * candidate set.
	 */
	private static Scope testUniverseScope() {
		ModuleIndex testIndex = JandexIndexLoader.testIndex();
		return testIndex.universeScope();
	}

	/**
	 * Builds an AOT container for the given scope, using the test index (production
	 * {@code jandex.idx} merged with {@code jandex-test.idx}).
	 *
	 * <p>
	 * Merging the test index is what makes the AOT engine see the <em>same</em>
	 * universe as the Runtime engine under test — without it, AOT would only ever
	 * observe production classes and silently diverge from Runtime on any test that
	 * exercises a test bean. The scope predicate still gates discovery, so the
	 * merged index merely ensures condition targets and test-bean types are
	 * resolvable, exactly as Runtime sees them.
	 * </p>
	 *
	 * <p>
	 * Unlike the production AOT path (which compiles the full {@code jandex.idx}
	 * universe via {@code SummerMojo}), the test path uses the test index so test
	 * beans are part of the generated graph.
	 * </p>
	 */
	private static BeanContainer buildAot(AotKey key, Scope scope, List<MockedBean> mocks) {
		try {
			IndexView index = JandexIndexLoader.testIndex().index();
			Class<?> aotEngine = Class.forName("summer.aot.AotEngine");
			MockedBean[] mockedBeans = mocks.toArray(new MockedBean[0]);
			java.lang.reflect.Method buildAndCompile = aotEngine.getMethod("buildAndCompile", IndexView.class,
					Scope.class, String.class, String.class, MockedBean[].class);
			return (BeanContainer) buildAndCompile.invoke(null, index, scope, key.cacheKey(), key.className(),
					mockedBeans);
		} catch (Exception e) {
			throw new RuntimeException("Failed to build AOT container. Ensure summer-aot-engine is on the classpath.",
					e);
		}
	}

	/**
	 * True if the class is a bean (carries {@code @Component} or a
	 * meta-annotation).
	 */
	public static boolean isComponent(Class<?> clazz) {
		if (clazz.isAnnotationPresent(Component.class)) {
			return true;
		}
		for (java.lang.annotation.Annotation ann : clazz.getAnnotations()) {
			if (ann.annotationType().isAnnotationPresent(Component.class)) {
				return true;
			}
		}
		return false;
	}
}
