package summer.test;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import org.jboss.jandex.IndexView;
import summer.core.BeanContainer;
import summer.core.Component;
import summer.core.Engine;
import summer.core.bean.ModuleIndex;
import summer.core.bean.Scope;
import summer.runtime.JandexIndexLoader;
import summer.runtime.RuntimeBeanContainerBuilder;
import summer.test.annotation.SummerTest;

/**
 * Unified test container builder for Summer DI engines.
 *
 * <p>
 * <b>User path</b> (engine transparent — always Runtime in dev mode):
 * </p>
 *
 * <pre>{@code
 * Testing.build();
 * Testing.build(AppConfig.class);
 * Testing.buildWithExternal(new Class<?>[]{AppConfig.class}, someBean);
 * Testing.scan("com.myapp");
 * }</pre>
 *
 * <p>
 * <b>TCK path</b> (explicit engine for dual-engine verification):
 * </p>
 *
 * <pre>{@code
 * Testing.build(Engine.RUNTIME, seeds);
 * Testing.build(Engine.AOT, seeds);
 * }</pre>
 */
public final class Testing {

	private Testing() {
	}

	// ── User path: engine transparent ───────────────────────────────────

	/** Builds a container using the Runtime engine (dev mode default). */
	public static BeanContainer build(Class<?>... seeds) {
		return build(Engine.RUNTIME, seeds);
	}

	/**
	 * Builds a Runtime container restricted to an explicit {@link Scope}. The scope
	 * is typically produced by {@link #scopeFor(Class)} from a {@code @SummerTest}
	 * class, but any scope works (e.g. a hand-written package scope for a one-off
	 * integration test).
	 */
	public static BeanContainer build(Scope scope, Object... externalBeans) {
		return RuntimeBeanContainerBuilder.build(scope, externalBeans);
	}

	public static BeanContainer buildWithExternal(Class<?>[] seeds, Object... externalBeans) {
		if (seeds.length > 0) {
			return RuntimeBeanContainerBuilder.buildFromSeedsWithExternal(seeds, externalBeans);
		}
		return RuntimeBeanContainerBuilder.buildFromTestIndex(externalBeans);
	}

	/**
	 * Auto-scans a package tree for components. {@code seeds} are additional
	 * classes outside the target package.
	 */
	public static BeanContainer scan(String basePackage, Class<?>... seeds) {
		return RuntimeBeanContainerBuilder.buildModuleWithExternal(basePackage, seeds);
	}

	// ── TCK path: explicit engine ───────────────────────────────────

	public static BeanContainer build(Engine engine, Class<?>... seeds) {
		if (engine == Engine.RUNTIME) {
			return buildFromSeeds(seeds);
		}
		// TCK full-application verification. An empty seed set spans the strict
		// universe of every PRODUCTION module (never Scope.classpath() — that would
		// pull in unrelated test classes and defeat isolation). Seeds narrow the
		// universe to the exact named set.
		Scope scope = seeds.length > 0 ? productionScopeFromSeeds(seeds) : productionScope();
		String tag = seeds.length > 0 ? "seeds=" + Arrays.toString(seeds) : "all-modules";
		return buildAot(AotKey.forUniverse(tag), scope, new Object[0]);
	}

	// ── Utility ─────────────────────────────────────────────────────

	public static boolean isComponent(Class<?> clazz) {
		if (clazz.isAnnotationPresent(Component.class)) {
			return true;
		}
		for (Annotation ann : clazz.getAnnotations()) {
			if (ann.annotationType().isAnnotationPresent(Component.class)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Resolves the bean {@link Scope} for a {@code @SummerTest} class.
	 *
	 * <p>
	 * Quarkus-aligned default: the <b>whole production application</b> is in scope
	 * (every production bean across all modules, transitively). A test sees the
	 * same universe as {@code @QuarkusTest} does — there is no narrow-module
	 * surprise and no manual seed list to maintain. Isolation between tests is
	 * achieved the Quarkus way: via {@code @TestProfile} (configuration variants)
	 * and {@code @Mock} (bean stubs), not by restricting the discovery universe.
	 * Both DI engines observe this identical universe, which is exactly what
	 * framework-enforced dual-engine consistency requires.
	 * </p>
	 *
	 * <p>
	 * The {@code modules} / {@code packages} attributes on {@link SummerTest} are an
	 * <em>optional narrowing</em> on top of the Quarkus model: they shrink the
	 * universe to the named modules (plus a test's own module) or package trees.
	 * This is a Summer extension for multi-module setups where a test genuinely
	 * only needs a slice — it never widens the universe beyond production beans,
	 * so it cannot pull in unrelated test classes (the sad-path trap). When
	 * neither is set, the full production universe applies, matching
	 * {@code @QuarkusTest} semantics.
	 * </p>
	 *
	 * <p>
	 * The resolved scope is shared verbatim by both engines: the Runtime engine
	 * builds from {@code buildModuleIndex()} (production classes only) and the AOT
	 * engine derives its container identity from the same {@code modules} /
	 * {@code packages} / own-module dimensions (see {@link AotKey#forTest}). This
	 * keeps the two engines' universes symmetric — a precondition for the
	 * dual-engine consistency guarantee.
	 * </p>
	 *
	 * @param testClass
	 *            the annotated test class
	 * @return the production universe scope, optionally narrowed by
	 *         {@code modules}/{@code packages}
	 */
	public static Scope scopeFor(Class<?> testClass) {
		ModuleIndex moduleIndex = JandexIndexLoader.buildModuleIndex();
		SummerTest ann = testClass.getAnnotation(SummerTest.class);
		Set<String> narrowedModules = narrowModules(ann);
		Set<String> narrowedPackages = narrowPackages(ann);
		if (narrowedModules.isEmpty() && narrowedPackages.isEmpty()) {
			// Quarkus-style wide default: every production module.
			return moduleIndex.productionScope();
		}
		// Optional narrowing: explicitly named modules plus any package-tree
		// prefixes. Test classes are never in scope, because buildModuleIndex()
		// excludes jandex-test.idx from the bean universe.
		Set<String> allowed = moduleIndex.classesInModules(narrowedModules);
		Set<String> prefixes = new LinkedHashSet<>(narrowedPackages);
		return name -> allowed.contains(name) || prefixes.stream().anyMatch(name::startsWith);
	}

	/**
	 * Collects the module set a test wants in scope. Returns an empty set when the
	 * test opts into the wide (all-modules) default, so the caller can fall back to
	 * {@link ModuleIndex#productionScope()}.
	 */
	private static Set<String> narrowModules(SummerTest ann) {
		Set<String> mods = new LinkedHashSet<>();
		if (ann != null) {
			for (String m : ann.modules()) {
				if (!m.isEmpty()) {
					mods.add(m);
				}
			}
		}
		return mods;
	}

	/** Package-tree prefixes declared via {@link SummerTest#packages()}, if any. */
	private static Set<String> narrowPackages(SummerTest ann) {
		Set<String> pkgs = new LinkedHashSet<>();
		if (ann != null) {
			for (String p : ann.packages()) {
				if (!p.isEmpty()) {
					pkgs.add(p.endsWith(".") ? p : p + ".");
				}
			}
		}
		return pkgs;
	}

	/**
	 * Single source of truth for building a {@link Scope} from a resolved set of
	 * allowed class names plus optional package prefixes. Both unit tests
	 * ({@link #scopeFor(Class)}) and integration tests
	 * ({@link #buildForIntegration}) funnel through here, so discovery boundaries
	 * share one mechanism.
	 */
	private static Scope scopeFromAllowed(Set<String> allowed, Set<String> packagePrefixes) {
		return name -> {
			if (allowed.contains(name)) {
				return true;
			}
			for (String prefix : packagePrefixes) {
				if (name.startsWith(prefix)) {
					return true;
				}
			}
			return false;
		};
	}

	// ── Module detection ────────────────────────────────────────────

	/**
	 * Builds a container scoped to a {@code @SummerTest} class, on the requested
	 * engine.
	 *
	 * <p>
	 * The scope is derived from the test class via {@link #scopeFor(Class)}: its
	 * own module by default, widened by any {@code modules}/{@code packages}
	 * attributes. Both engines receive the <em>identical</em> scope, so a test
	 * verified on Runtime and AOT sees the same candidate universe — the foundation
	 * of framework-enforced dual-engine consistency.
	 * </p>
	 *
	 * @param testClass
	 *            the test class to scope the container for
	 * @param engine
	 *            Runtime or AOT
	 * @param externalBeans
	 *            pre-instantiated beans to register (e.g. mocks)
	 * @return immutable bean container
	 */
	public static BeanContainer buildForTest(Class<?> testClass, Engine engine, Object... externalBeans) {
		Scope scope = scopeFor(testClass);
		if (engine == Engine.AOT) {
			return buildAot(AotKey.forTest(testClass), scope, externalBeans);
		}
		return build(scope, externalBeans);
	}

	/**
	 * Builds an AOT container for the given scope, using the test-aware index
	 * (production {@code jandex.idx} merged with {@code jandex-test.idx}).
	 *
	 * <p>
	 * Merging the test index is what makes the AOT engine see the <em>same</em>
	 * universe as the Runtime engine under test — without it, AOT would only ever
	 * observe production classes and silently diverge from Runtime on any test that
	 * exercises a fixture bean. The scope predicate still gates discovery, so the
	 * strict module boundary from {@link #scopeFor(Class)} is preserved; the merged
	 * index merely ensures condition targets and fixture types are resolvable,
	 * exactly as Runtime sees them.
	 * </p>
	 *
	 * <p>
	 * Unlike the production AOT path ({@code SummerApplication.run(AOT)}, which
	 * compiles the full {@code jandex.idx} universe), the test path never falls
	 * back to {@link Scope#classpath()} — the scope is always explicit, so a test
	 * can never accidentally scan unrelated classes (the sad-path trap).
	 * </p>
	 */
	private static BeanContainer buildAot(AotKey key, Scope scope, Object... externalBeans) {
		try {
			IndexView index = JandexIndexLoader.buildTestModuleIndex().index();
			// Identity (cache key + generated class name) is derived from the inputs
			// that actually shape the AOT graph — module boundary, profile content,
			// and mocked types — via AotKey. Config is bound at generation time and
			// baked into the generated code, so the identity must encode it or two
			// tests differing only by @TestProfile would silently share a container.
			Class<?> aotEngine = Class.forName("summer.aot.AotEngine");
			Method buildAndCompile = aotEngine.getMethod("buildAndCompile", IndexView.class, Scope.class, String.class,
					String.class, Object[].class);
			return (BeanContainer) buildAndCompile.invoke(null, index, scope, key.cacheKey(), key.className(),
					externalBeans);
		} catch (Exception e) {
			throw new RuntimeException("Failed to build AOT container. Ensure summer-aot-engine is on the classpath.",
					e);
		}
	}

	/**
	 * Strict scope over every production module — used by TCK full-application
	 * verification when no seeds narrow the universe. Derived from the production
	 * index only (never {@link Scope#classpath()}), so it spans all production
	 * beans without leaking test classes.
	 */
	private static Scope productionScope() {
		ModuleIndex productionIndex = JandexIndexLoader.buildProductionModuleIndex();
		Set<String> allowed = new java.util.HashSet<>();
		for (String mod : productionIndex.modules()) {
			allowed.addAll(productionIndex.classesInModule(mod, Set.of()));
		}
		return scopeFromAllowed(allowed, Set.of());
	}

	/** Strict scope over an explicit seed set (exact-match, no expansion). */
	private static Scope productionScopeFromSeeds(Class<?>... seeds) {
		Set<String> seedNames = toNames(seeds);
		return seedNames::contains;
	}

	/**
	 * Builds a Runtime container scoped to a {@code @SummerTest} class (dev-mode
	 * default engine). See {@link #buildForTest(Class, Engine, Object...)}.
	 *
	 * @deprecated retained for callers that hard-code the Runtime engine; prefer
	 *             {@link #buildForTest(Class, Engine, Object...)}
	 */
	@Deprecated
	public static BeanContainer buildForTest(Class<?> testClass, Object... externalBeans) {
		return buildForTest(testClass, Engine.RUNTIME, externalBeans);
	}

	/**
	 * Builds a container for an integration test that starts the full application
	 * stack (e.g. a Netty server) and depends on one or more module-local test
	 * fixtures.
	 *
	 * <p>
	 * The scope includes every <b>production</b> bean (attributed by
	 * {@code jandex.idx}) so the web/DI stack is wired transitively, plus the
	 * explicitly named test-fixture classes (e.g. a {@code @Configuration} that
	 * registers routes). Other test fixtures in the same module are excluded, which
	 * prevents the ambiguity that would arise if two fixture configs declared a
	 * bean of the same type and both ended up in scope (e.g. two {@code @Bean}
	 * {@code WsRouteProvider} definitions).
	 * </p>
	 *
	 * <p>
	 * Every named fixture must be indexed by Jandex (in {@code jandex.idx} or
	 * {@code jandex-test.idx}). A fixture that is not indexed is a build
	 * configuration gap, not a silent no-op — we fail loudly rather than dropping
	 * it, so a misconfigured integration test can never report green while its
	 * fixture never took effect (the Twitter IT false-green trap).
	 * </p>
	 *
	 * @param testClass
	 *            the integration test class (used for index attribution)
	 * @param fixtureSeeds
	 *            test-fixture classes to add to the production scope
	 * @param externalBeans
	 *            pre-instantiated beans to register (e.g. the middleware chain)
	 * @return immutable bean container
	 */
	public static BeanContainer buildForIntegration(Class<?> testClass, Class<?>[] fixtureSeeds,
			Object... externalBeans) {
		// Discovery index includes test-class fixtures (jandex-test.idx), so
		// fixture @Component/@Configuration classes are scannable.
		ModuleIndex testIndex = JandexIndexLoader.buildTestModuleIndex();
		// Attribution index covers every production module (jandex.idx) — used
		// to decide which classes belong to the production stack.
		ModuleIndex productionIndex = JandexIndexLoader.buildProductionModuleIndex();

		// Loud validation: every named fixture must be present in some Jandex
		// index (production or test). A missing fixture means it was never
		// registered — fail here instead of letting discoverComponents silently
		// skip it and the test pass on the wrong (production-only) universe.
		for (Class<?> fixture : fixtureSeeds) {
			String name = fixture.getName();
			boolean indexed = productionIndex.moduleOf(name) != null || testIndex.moduleOf(name) != null;
			if (!indexed) {
				throw new IllegalStateException("Test fixture " + name + " is not indexed by Jandex. "
						+ "Ensure the test module runs the jandex-maven-plugin over its test-classes "
						+ "(output META-INF/jandex-test.idx under target/test-classes) and that the class "
						+ "carries @Component / @Configuration.");
			}
		}

		// Scope = every production bean + the explicitly named fixtures. Derived
		// through the same ModuleIndex mechanism as scopeFor: allowed = union of
		// all production modules' classes, widened by the fixture names.
		Set<String> allowed = new java.util.HashSet<>();
		for (String mod : productionIndex.modules()) {
			allowed.addAll(productionIndex.classesInModule(mod, Set.of()));
		}
		Set<String> fixtureNames = new LinkedHashSet<>();
		for (Class<?> seed : fixtureSeeds) {
			fixtureNames.add(seed.getName());
		}
		allowed.addAll(fixtureNames);

		Scope scope = scopeFromAllowed(allowed, Set.of());
		return RuntimeBeanContainerBuilder.build(testIndex, scope, externalBeans);
	}

	// ── Internals ───────────────────────────────────────────────────

	private static BeanContainer buildFromSeeds(Class<?>... seeds) {
		return seeds.length > 0
				? RuntimeBeanContainerBuilder.buildFromSeeds(seeds)
				: RuntimeBeanContainerBuilder.build();
	}

	private static Set<String> toNames(Class<?>... seeds) {
		Set<String> names = new LinkedHashSet<>();
		for (Class<?> seed : seeds) {
			names.add(seed.getName());
		}
		return names;
	}
}
