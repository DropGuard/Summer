package com.github.dropguard.summer.test;

import com.github.dropguard.summer.core.ApplicationRunner;
import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.core.Engine;
import com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.core.bean.MockedBean;
import com.github.dropguard.summer.runtime.JandexIndexLoader;
import com.github.dropguard.summer.test.annotation.TestProfile;
import java.lang.reflect.Constructor;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.extension.TestInstantiationException;

/**
 * JVM-wide universe cache, container construction, and test-instance injection.
 *
 * <p>The single owner of every Summer test path: container build, shouldFail contract, constructor
 * injection, and per-class caching. {@link SummerExtension} and {@link
 * DualEngineInvocationProvider} are thin adapters that delegate here.
 */
@Internal
public final class SummerTestLifecycle {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(SummerTestLifecycle.class);

    private static final SummerTestLifecycle INSTANCE = new SummerTestLifecycle();

    private final Map<UniverseKey, CachedUniverse> universeCache = new HashMap<>();
    private final AtomicLong cacheHits = new AtomicLong();

    // Shared index DTOs, owned by this JVM-singleton instance (not static fields on the loader
    // classes): the classpath and the module's test-classes directory are immutable during a JVM
    // run, so each is loaded once per run and threaded into container builds — a full classpath
    // sweep / test-classes walk happens once, not once per distinct @SummerTest universe. Cleared
    // in shutdown() alongside universeCache.
    private volatile JandexIndexLoader.LoadedIndex productionIndex;
    // ConcurrentHashMap: computeIfAbsent in TestClassIndexer must be atomic even if a future
    // surefire/JUnit parallel configuration runs test classes concurrently.
    private final java.util.Map<java.nio.file.Path, org.jboss.jandex.Index> testIndexCache =
            new java.util.concurrent.ConcurrentHashMap<>();

    private JandexIndexLoader.LoadedIndex productionIndex() {
        JandexIndexLoader.LoadedIndex p = productionIndex;
        if (p == null) {
            synchronized (this) {
                p = productionIndex;
                if (p == null) {
                    productionIndex = p = JandexIndexLoader.productionIndex();
                }
            }
        }
        return p;
    }

    private SummerTestLifecycle() {
        Runtime.getRuntime()
                .addShutdownHook(new Thread(this::shutdown, "summer-test-lifecycle-shutdown"));
    }

    public static SummerTestLifecycle instance() {
        return INSTANCE;
    }

    // ── public entry ──────────────────────────────────────────────────

    /**
     * Builds the container, instantiates the test class, and enforces the shouldFail contract.
     * Returns both the instance and the container.
     */
    public static BuildOutcome createUniverse(Class<?> testClass, Engine engine) {
        SummerTestExtension config = SummerTestExtension.resolve(testClass);
        boolean shouldFail = config != null && config.shouldFail();

        List<MockedBean> mocks = createMocks(testClass);

        BeanContainer container;
        boolean fresh;
        try {
            var result = INSTANCE.acquireUniverse(testClass, engine, mocks, config);
            container = result.container();
            fresh = result.fresh();
        } catch (TestResourceStartupException e) {
            // A broken external resource is infrastructure failure, never the negative test's
            // intent — it must surface even under shouldFail (the promise is "assembly fails",
            // not "anything throws").
            throw e;
        } catch (Exception buildFailure) {
            if (shouldFail) {
                return new BuildOutcome(instantiateWithoutContainer(testClass), null);
            }
            throw new TestInstantiationException(
                    "@SummerTest container failed to assemble for "
                            + testClass.getSimpleName()
                            + " (engine="
                            + engine
                            + "). Declare shouldFail=true if this is a negative test.",
                    buildFailure);
        }

        if (shouldFail) {
            throw new TestInstantiationException(
                    "@SummerTest(shouldFail=true) on "
                            + testClass.getSimpleName()
                            + " (engine="
                            + engine
                            + ") expected assembly to fail, but the container built successfully."
                            + " The negative contract is violated - the graph was accepted when it"
                            + " should have been rejected.");
        }
        // Start application runners (HTTP server, gRPC, etc.) — mirrors
        // SummerApplication's post-build hook — but only when the container was freshly built:
        // the cached-container path (e.g. the RUNTIME invocation of a @DualEngine test, which
        // reuses the
        // universe SummerExtension already built) must not re-run NettyServerRunner and bind the
        // port a second time.
        if (fresh) {
            for (ApplicationRunner runner : container.getBeans(ApplicationRunner.class)) {
                try {
                    runner.run(container);
                } catch (Exception e) {
                    throw new TestInstantiationException(
                            "Failed to start ApplicationRunner "
                                    + runner.getClass().getSimpleName(),
                            e);
                }
            }
        }
        Object instance = instantiate(testClass, container);
        // Resource field injection runs after the constructor injection (Quarkus' ordering):
        // the container's constructor args resolve first, then @TestResource values (mapped
        // ports, URLs, clients) land in the test's fields.
        TestResources.injectInto(testClass, instance);
        return new BuildOutcome(instance, container);
    }

    /** The result of {@link #createUniverse}. */
    public record BuildOutcome(Object instance, BeanContainer container) {}

    // ── container build + caching ─────────────────────────────────────

    /** A universe lookup result: the container plus whether it was freshly built (vs cache hit). */
    private record UniverseResult(BeanContainer container, boolean fresh) {}

    private UniverseResult acquireUniverse(
            Class<?> testClass, Engine engine, List<MockedBean> mocks, SummerTestExtension config) {
        Map<String, Object> overrides = new HashMap<>(profileOverrides(testClass));
        // TestResource properties win over profile overrides
        overrides.putAll(TestResources.resolveProperties(testClass));
        UniverseKey key = universeKeyFor(testClass, engine, mocks, overrides);
        CachedUniverse cached = universeCache.get(key);
        if (cached != null) {
            cacheHits.incrementAndGet();
            return new UniverseResult(cached.container(), false);
        }

        Class<?>[] seeds = config != null ? config.beanClasses() : new Class<?>[0];
        BeanContainer built =
                TestContainer.builder()
                        .testClass(testClass)
                        .engine(engine)
                        .mocks(mocks)
                        .overrides(overrides)
                        .beans(seeds)
                        .withIndexes(productionIndex(), testIndexCache)
                        .build();
        universeCache.put(key, new CachedUniverse(built));
        return new UniverseResult(built, true);
    }

    public long cacheHits() {
        return cacheHits.get();
    }

    // ── constructor injection ─────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private static Object instantiate(Class<?> testClass, BeanContainer container) {
        Constructor<?> ctor = singleConstructor(testClass);
        Type[] genericParamTypes = ctor.getGenericParameterTypes();
        Object[] args = new Object[genericParamTypes.length];
        for (int i = 0; i < genericParamTypes.length; i++) {
            Type paramType = genericParamTypes[i];
            if (paramType == BeanContainer.class) {
                args[i] = container;
            } else if (paramType instanceof ParameterizedType pt
                    && pt.getRawType() == List.class
                    && pt.getActualTypeArguments().length == 1
                    && pt.getActualTypeArguments()[0] instanceof Class<?> elementType) {
                args[i] = container.getBeans(elementType);
            } else if (paramType instanceof Class<?> clazz) {
                try {
                    args[i] = container.getBean(clazz);
                } catch (Exception e) {
                    throw new TestInstantiationException(
                            "Cannot resolve constructor parameter "
                                    + clazz.getSimpleName()
                                    + " for @SummerTest "
                                    + testClass.getSimpleName(),
                            e);
                }
            } else {
                throw new TestInstantiationException(
                        "Unsupported constructor parameter type "
                                + paramType.getTypeName()
                                + " for @SummerTest "
                                + testClass.getSimpleName());
            }
        }
        try {
            return ctor.newInstance(args);
        } catch (Exception e) {
            throw new TestInstantiationException(
                    "Failed to create @SummerTest instance: " + testClass.getName(), e);
        }
    }

    private static Object instantiateWithoutContainer(Class<?> testClass) {
        Constructor<?> ctor = singleConstructor(testClass);
        for (Class<?> paramType : ctor.getParameterTypes()) {
            if (paramType != BeanContainer.class) {
                throw new TestInstantiationException(
                        "@SummerTest(shouldFail=true) class "
                                + testClass.getSimpleName()
                                + " uses a constructor parameter of type "
                                + paramType.getSimpleName()
                                + ", but a failed build provides no container to inject it from.");
            }
        }
        try {
            return ctor.newInstance();
        } catch (Exception e) {
            throw new TestInstantiationException(
                    "Failed to create @SummerTest instance: " + testClass.getName(), e);
        }
    }

    private static Constructor<?> singleConstructor(Class<?> testClass) {
        Constructor<?>[] ctors = testClass.getDeclaredConstructors();
        if (ctors.length != 1) {
            throw new TestInstantiationException(
                    "@SummerTest class "
                            + testClass.getName()
                            + " must have exactly one constructor. Found: "
                            + ctors.length);
        }
        ctors[0].setAccessible(true);
        return ctors[0];
    }

    private static List<MockedBean> createMocks(Class<?> testClass) {
        List<MockedBean> mocks = new ArrayList<>();
        for (Class<?> mockedType : MockedParams.scan(testClass)) {
            mocks.add(MockedBean.of(mockedType, SummerExtension.createMock(mockedType)));
        }
        return mocks;
    }

    // ── lifecycle ─────────────────────────────────────────────────────

    /**
     * Suite-end telemetry: the universe reuse (the cache hit count) is a design guarantee — the
     * RUNTIME invocation of a {@code @DualEngine} test reuses the universe SummerExtension built;
     * the AOT invocation reuses across the class's methods. Surfacing the count at the suite end
     * makes a silently-never-reused cache visible in the build log instead of being a dead counter.
     * Invoked by the ServiceLoader-registered {@link UniverseCacheTelemetryListener} (a separate
     * class with a public no-arg constructor — the singleton's private constructor cannot satisfy
     * the ServiceLoader contract).
     */
    public void logCacheTelemetry() {
        log.info(
                "[Summer] test universe cache: {} hits / {} universes",
                cacheHits.get(),
                universeCache.size());
    }

    public synchronized void shutdown() {
        for (CachedUniverse c : universeCache.values()) {
            try {
                c.container().close();
            } catch (Exception ignored) {
            }
        }
        universeCache.clear();
        testIndexCache.clear();
        productionIndex = null;
        TestResources.shutdown();
    }

    // ── helpers ───────────────────────────────────────────────────────

    private UniverseKey universeKeyFor(
            Class<?> testClass,
            Engine engine,
            List<MockedBean> mocks,
            Map<String, Object> overrides) {
        String profile = overrides.isEmpty() ? UniverseKey.NO_PROFILE : overrides.toString();
        SortedSet<String> mocked = new TreeSet<>();
        for (MockedBean m : mocks) {
            mocked.add(m.targetType().getName());
        }
        return UniverseKey.of(profile, List.copyOf(mocked), engine, testClass.getName());
    }

    private Map<String, Object> profileOverrides(Class<?> testClass) {
        TestProfile ann = testClass.getAnnotation(TestProfile.class);
        if (ann == null) return Map.of();
        try {
            return ann.value().getDeclaredConstructor().newInstance().configOverrides();
        } catch (Exception e) {
            return Map.of();
        }
    }

    private record CachedUniverse(BeanContainer container) {}
}
