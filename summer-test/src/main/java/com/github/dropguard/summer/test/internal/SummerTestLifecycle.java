package com.github.dropguard.summer.test.internal;

import com.github.dropguard.summer.core.ApplicationRunner;
import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.core.Engine;
import com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.core.bean.MockedBean;
import com.github.dropguard.summer.test.SummerTestExtension;
import com.github.dropguard.summer.test.TestContainer;
import com.github.dropguard.summer.test.annotation.Mock;
import com.github.dropguard.summer.test.annotation.TestProfile;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.extension.ExtensionContext;
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

    private static final SummerTestLifecycle INSTANCE = new SummerTestLifecycle();

    private final Map<EnvKey, CachedUniverse> universeCache = new HashMap<>();
    private final AtomicLong cacheHits = new AtomicLong();

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
    public static BuildOutcome createUniverse(
            Class<?> testClass, Engine engine, ExtensionContext extensionContext) {
        SummerTestExtension config = SummerTestExtension.resolve(testClass);
        boolean shouldFail = config != null && config.shouldFail();

        List<MockedBean> mocks = createMocks(testClass);

        BeanContainer container;
        try {
            container = INSTANCE.acquireUniverse(testClass, engine, mocks, config);
        } catch (Exception buildFailure) {
            if (!shouldFail) {
                throw new TestInstantiationException(
                        "@SummerTest container failed to assemble for "
                                + testClass.getSimpleName()
                                + " (engine="
                                + engine
                                + "). Declare shouldFail=true if this is a negative test.",
                        buildFailure);
            }
            return new BuildOutcome(instantiateWithoutContainer(testClass), null);
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
        // SummerApplication's post-build hook.
        for (ApplicationRunner runner : container.getBeans(ApplicationRunner.class)) {
            try {
                runner.run(container);
            } catch (Exception e) {
                throw new TestInstantiationException(
                        "Failed to start ApplicationRunner " + runner.getClass().getSimpleName(),
                        e);
            }
        }
        return new BuildOutcome(instantiate(testClass, container), container);
    }

    /** The result of {@link #createUniverse}. */
    public record BuildOutcome(Object instance, BeanContainer container) {}

    // ── container build + caching ─────────────────────────────────────

    BeanContainer acquireUniverse(
            Class<?> testClass, Engine engine, List<MockedBean> mocks, SummerTestExtension config) {
        Map<String, Object> overrides = new HashMap<>(profileOverrides(testClass));
        // TestResource properties win over profile overrides
        overrides.putAll(TestResources.resolveProperties(testClass));
        EnvKey key = envKeyFor(testClass, mocks, overrides);
        CachedUniverse cached = universeCache.get(key);
        if (cached != null) {
            cacheHits.incrementAndGet();
            return cached.container();
        }

        Class<?>[] seeds = config != null ? config.beanClasses() : new Class<?>[0];
        BeanContainer built =
                TestContainer.builder()
                        .testClass(testClass)
                        .engine(engine)
                        .mocks(mocks)
                        .overrides(overrides)
                        .beans(seeds)
                        .build();
        universeCache.put(key, new CachedUniverse(built));
        return built;
    }

    public long cacheHits() {
        return cacheHits.get();
    }

    // ── constructor injection ─────────────────────────────────────────

    private static Object instantiate(Class<?> testClass, BeanContainer container) {
        Constructor<?> ctor = singleConstructor(testClass);
        Class<?>[] paramTypes = ctor.getParameterTypes();
        Object[] args = new Object[paramTypes.length];
        for (int i = 0; i < paramTypes.length; i++) {
            if (paramTypes[i] == BeanContainer.class) {
                args[i] = container;
            } else {
                try {
                    args[i] = container.getBean(paramTypes[i]);
                } catch (Exception e) {
                    throw new TestInstantiationException(
                            "Cannot resolve constructor parameter "
                                    + paramTypes[i].getSimpleName()
                                    + " for @SummerTest "
                                    + testClass.getSimpleName(),
                            e);
                }
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
        Constructor<?>[] ctors = testClass.getDeclaredConstructors();
        if (ctors.length != 1) {
            return mocks;
        }
        Annotation[][] paramAnnotations = ctors[0].getParameterAnnotations();
        Class<?>[] paramTypes = ctors[0].getParameterTypes();
        for (int i = 0; i < paramTypes.length; i++) {
            for (Annotation ann : paramAnnotations[i]) {
                if (ann instanceof Mock) {
                    mocks.add(
                            MockedBean.of(
                                    paramTypes[i], SummerExtension.createMock(paramTypes[i])));
                }
            }
        }
        return mocks;
    }

    // ── lifecycle ─────────────────────────────────────────────────────

    public synchronized void shutdown() {
        for (CachedUniverse c : universeCache.values()) {
            try {
                c.container().close();
            } catch (Exception ignored) {
            }
        }
        universeCache.clear();
        TestResources.shutdown();
    }

    // ── helpers ───────────────────────────────────────────────────────

    private EnvKey envKeyFor(
            Class<?> testClass, List<MockedBean> mocks, Map<String, Object> overrides) {
        String profile = overrides.isEmpty() ? EnvKey.NO_PROFILE : overrides.toString();
        SortedSet<String> mocked = new TreeSet<>();
        for (MockedBean m : mocks) {
            mocked.add(m.targetType().getName());
        }
        return EnvKey.of(profile, List.copyOf(mocked), testClass.getName());
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
