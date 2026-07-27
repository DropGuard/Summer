package com.github.dropguard.summer.test;

import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.core.Component;
import com.github.dropguard.summer.core.Engine;
import com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.core.bean.MockedBean;
import com.github.dropguard.summer.runtime.JandexIndexLoader;
import com.github.dropguard.summer.runtime.RuntimeBeanContainerBuilder;
import com.github.dropguard.summer.runtime.TestClassIndexer;
import com.github.dropguard.summer.test.internal.AotKey;
import com.github.dropguard.summer.test.internal.NarrowIndexBuilder;
import java.util.List;
import org.jboss.jandex.IndexView;

/**
 * Unified test container builder for Summer's DI engines.
 *
 * <p>A {@code @SummerTest} container always spans the <b>test universe</b>: the whole application
 * (every production bean across all modules) plus whatever test beans are on the classpath —
 * controllers, stub configs, route fixtures. This mirrors Quarkus' {@code @QuarkusTest}: there is
 * no seed list, no module narrowing, no fixture switch. A test bean is discovered exactly like a
 * production bean — if it is indexed and in scope, the container wires it. Isolation between tests
 * comes from {@code @TestProfile} (configuration variants) and {@code @Mock} (bean stubs), never
 * from shrinking the discovery universe. Mocks are declared with {@code @Mock} on a test
 * constructor parameter and supplied by the framework — there is no hand-rolled instance collection
 * to register (a concept Quarkus does not expose).
 *
 * <p>Both DI engines observe this identical universe, which is the foundation of framework-enforced
 * dual-engine consistency: a test verified on Runtime and AOT sees the same candidate set.
 */
public final class Testing {

    private Testing() {}

    // ── User path: engine transparent ───────────────────────────────────

    /**
     * Builds a container over the full test universe using the Runtime engine (dev mode default).
     * Equivalent to a {@code @SummerTest} with no extra configuration.
     */
    public static BeanContainer build() {
        return buildForTest(null);
    }

    // ── TCK path: explicit engine, identical universe ───────────────────

    /**
     * Builds a container scoped to a {@code @SummerTest} class, on the requested engine.
     *
     * <p>The universe is always the test universe (application + test beans), shared verbatim by
     * both engines — the precondition for dual-engine consistency. Mocks are supplied as {@link
     * MockedBean}s (one bound struct per {@code @Mock} parameter, carrying the target type and its
     * Mockito instance); the discovery stage removes the real bean of each target type so it is
     * never instantiated. There is no hand-built instance collection — mocks are declared, not
     * assembled by the caller (a concept Quarkus does not expose).
     *
     * @param testClass the annotated test class
     * @param engine Runtime or AOT
     * @param mocks mocked beans produced from {@code @Mock} parameters (internal — not a public
     *     registration API)
     * @return immutable bean container
     */
    @Internal
    public static BeanContainer buildForTest(
            Class<?> testClass, Engine engine, List<MockedBean> mocks) {
        return buildForTest(testClass, engine, mocks, java.util.Map.of());
    }

    /**
     * Builds a container scoped to a {@code @SummerTest} class, on the requested engine, applying
     * the resolved {@code @TestProfile} overrides.
     *
     * <p>The overrides are the single source of truth for both engines: the Runtime engine threads
     * them through {@link ConfigBinder.BindingContext} at binding time, and the AOT engine bakes
     * them into the generated {@code wire()} as the same {@code BindingContext} literal. Because
     * AOT resolves overrides at generation time there is no ThreadLocal to leak, and the two
     * engines can never observe divergent profile state.
     *
     * @param testClass the annotated test class
     * @param engine Runtime or AOT
     * @param mocks mocked beans produced from {@code @Mock} parameters (internal)
     * @param overrides resolved {@code @TestProfile} content (empty map when none)
     * @return immutable bean container
     */
    @Internal
    public static BeanContainer buildForTest(
            Class<?> testClass,
            Engine engine,
            List<MockedBean> mocks,
            java.util.Map<String, Object> overrides) {
        Class<?>[] seeds = summerTestSeeds(testClass);
        if (seeds.length > 0) {
            // Narrow (scoped) universe: build only from the explicit seeds plus
            // their transitive closure, Quarkus beanClasses(...) style. This is the
            // ONLY path that lets a sad-path fixture (which lives in its own module
            // and is never on the path of a whole-universe test-classes directory) be
            // discovered — NarrowIndexBuilder indexes the seed .class bytes directly
            // from the classpath, so it never depends on the default universe index.
            // Both engines must take this same branch so @DualEngine narrow tests
            // observe identical graphs.
            if (engine == Engine.AOT) {
                return buildNarrowAot(seeds, mocks, overrides);
            }
            org.jboss.jandex.IndexView narrowIndex = NarrowIndexBuilder.build(seeds);
            com.github.dropguard.summer.core.bean.BeanDeployment moduleIndex =
                    com.github.dropguard.summer.core.bean.BeanDeployment.forNarrow(narrowIndex);
            return RuntimeBeanContainerBuilder.build(moduleIndex, mocks, overrides);
        }
        if (engine == Engine.AOT) {
            return buildAot(AotKey.forTest(testClass, overrides), mocks, overrides);
        }
        return RuntimeBeanContainerBuilder.build(testUniverse(testClass), mocks, overrides);
    }

    /**
     * Returns the explicit seed classes declared on {@code @SummerTest(classes=...)}, or an empty
     * array when the test uses the whole-universe scope.
     */
    private static Class<?>[] summerTestSeeds(Class<?> testClass) {
        if (testClass == null) {
            return new Class<?>[0];
        }
        com.github.dropguard.summer.test.annotation.SummerTest ann =
                testClass.getAnnotation(
                        com.github.dropguard.summer.test.annotation.SummerTest.class);
        return (ann != null && ann.classes().length > 0) ? ann.classes() : new Class<?>[0];
    }

    /**
     * Builds a container for a {@code @SummerTest} class using the dev-mode default engine
     * (Runtime). See {@link #buildForTest(Class, Engine, List, Map)}.
     */
    public static BeanContainer buildForTest(Class<?> testClass) {
        return buildForTest(testClass, Engine.RUNTIME, List.of());
    }

    // ── Internals ─────────────────────────────────────────────────────

    /**
     * The whole-universe test deployment: the production application archives plus the current test
     * class's {@code test-classes} directory, indexed on demand.
     *
     * <p>This is a faithful copy of Quarkus' {@code @QuarkusTest} model: the test container
     * re-indexes <em>exactly</em> the running test class's {@code test-classes} directory (via
     * {@link TestClassIndexer}) and treats it as one additional archive, never a bulk scan of every
     * module's test classes and never a pre-baked {@code jandex-test.idx}. Mirrors the Runtime path
     * so both engines observe the identical candidate set — the foundation of dual-engine parity. A
     * negative (sad-path) fixture lives in its own module and is simply not on the path of this
     * {@code test-classes} directory, so no exclude list is needed.
     *
     * @param testClass the {@code @SummerTest} class (or any class on the test classpath); when
     *     {@code null}, the calling test class is inferred from the stack
     */
    private static com.github.dropguard.summer.core.bean.BeanDeployment testUniverse(
            Class<?> testClass) {
        JandexIndexLoader.LoadedIndex prod = JandexIndexLoader.productionIndex();
        Class<?> resolved = (testClass != null) ? testClass : TestClassIndexer.inferTestClass();
        org.jboss.jandex.IndexView testIndex =
                (resolved != null)
                        ? TestClassIndexer.indexTestClasses(resolved)
                        : org.jboss.jandex.CompositeIndex.create(java.util.List.of());
        // Combine per-archive attribution maps: every production module, plus the
        // test-classes directory as one "test" archive (Quarkus adds the test-classes
        // archive as a single additional archive).
        java.util.Map<String, String> classToArchive =
                new java.util.HashMap<>(prod.classToArchive());
        for (String type : testIndex.getKnownClasses().stream().map(Object::toString).toList()) {
            classToArchive.put(type, "test");
        }
        java.util.Map<String, org.jboss.jandex.IndexView> archiveIndexes =
                new java.util.HashMap<>(prod.archiveIndexes());
        archiveIndexes.put("test", testIndex);
        return com.github.dropguard.summer.core.bean.BeanDeployment.forTestUniverse(
                prod.index(), classToArchive, archiveIndexes);
    }

    private static BeanContainer buildAot(
            AotKey key, List<MockedBean> mocks, java.util.Map<String, Object> overrides) {
        try {
            com.github.dropguard.summer.core.bean.BeanDeployment moduleIndex = testUniverse(null);
            Class<?> aotEngine = Class.forName("com.github.dropguard.summer.aot.AotEngine");
            MockedBean[] mockedBeans = mocks.toArray(new MockedBean[0]);
            java.lang.reflect.Method buildAndCompile =
                    aotEngine.getMethod(
                            "buildAndCompile",
                            com.github.dropguard.summer.core.bean.BeanDeployment.class,
                            String.class,
                            String.class,
                            MockedBean[].class,
                            java.util.Map.class);
            return (BeanContainer)
                    buildAndCompile.invoke(
                            null,
                            moduleIndex,
                            key.cacheKey(),
                            key.className(),
                            mockedBeans,
                            overrides);
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to build AOT container. Ensure summer-aot-engine is on the classpath.",
                    e);
        }
    }

    /**
     * Builds an AOT container over an explicit narrow seed universe (Quarkus {@code
     * beanClasses(...)} shape). Used by scoped {@code @SummerTest(classes=...)} AOT builds and by
     * parity tests that must force the AOT dual-method generation path. The seed closure is indexed
     * transitively by {@link NarrowIndexBuilder} so discovery only ever sees the listed graph — no
     * production or unrelated test beans leak in.
     *
     * <p>Passes a NON-null (possibly empty) mocks array so the generated context emits the typed
     * {@code build(MockedBean[])} channel rather than the production-only {@code build(Object...)}
     * channel — this is the one AOT branch not exercised by the pre-generated single-method
     * context, so it is isolated here on purpose.
     */
    @Internal
    public static BeanContainer buildNarrowAot(
            Class<?>[] seeds, List<MockedBean> mocks, java.util.Map<String, Object> overrides) {
        String seedSignature =
                java.util.Arrays.stream(seeds)
                        .map(Class::getName)
                        .sorted()
                        .collect(java.util.stream.Collectors.joining(","));
        // The cache key MUST encode the mocked types, not just the seeds: two narrow
        // tests with the same seed closure but different @Mock sets must not share a
        // generated container (the generated graph differs by which real beans are
        // dropped for the mocks). forNarrow carries the seed signature; the mock
        // dimension is folded in here so the AOT compile cache keys on the full
        // closure+mocks, matching the documented contract of buildAndCompile's key.
        String mockSignature =
                mocks.stream()
                        .map(m -> m.targetTypeName())
                        .sorted()
                        .collect(java.util.stream.Collectors.joining(","));
        String fullSignature = seedSignature + "|mocks=" + mockSignature;
        AotKey key = AotKey.forNarrow(fullSignature);
        IndexView narrowIndex = NarrowIndexBuilder.build(seeds);
        // Build a BeanDeployment from the narrow index so discovery iterates only
        // the seed closure (honouring @SummerTest(classes=...)) instead of the
        // whole classpath — the previous IndexView-only path ignored the seeds.
        com.github.dropguard.summer.core.bean.BeanDeployment moduleIndex =
                com.github.dropguard.summer.core.bean.BeanDeployment.forNarrow(narrowIndex);
        try {
            Class<?> aotEngine = Class.forName("com.github.dropguard.summer.aot.AotEngine");
            MockedBean[] mockedBeans = mocks.toArray(new MockedBean[0]);
            java.lang.reflect.Method buildAndCompile =
                    aotEngine.getMethod(
                            "buildAndCompile",
                            com.github.dropguard.summer.core.bean.BeanDeployment.class,
                            String.class,
                            String.class,
                            MockedBean[].class,
                            java.util.Map.class);
            return (BeanContainer)
                    buildAndCompile.invoke(
                            null,
                            moduleIndex,
                            key.cacheKey(),
                            key.className(),
                            mockedBeans,
                            overrides);
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to build narrow AOT container for seeds "
                            + seedSignature
                            + ". Ensure summer-aot-engine is on the classpath.",
                    e);
        }
    }

    /** True if the class is a bean (carries {@code @Component} or a meta-annotation). */
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
