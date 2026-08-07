package com.github.dropguard.summer.test;

import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.core.Component;
import com.github.dropguard.summer.core.Engine;
import com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.core.bean.MockedBean;
import com.github.dropguard.summer.engine.BeanDeployment;
import com.github.dropguard.summer.engine.ContainerEngines;
import com.github.dropguard.summer.runtime.JandexIndexLoader;
import com.github.dropguard.summer.test.profile.SummerTestProfile;
import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jboss.jandex.IndexView;

/**
 * Programmatic entry point for building a Summer DI container in tests. Prefer {@code @SummerTest}
 * + constructor injection for the common case.
 *
 * <pre>{@code
 * // Full universe, default engine
 * TestContainer.build();
 *
 * // Full universe + config profile
 * TestContainer.builder()
 *         .testClass(MyTest.class)
 *         .profile(new DevProfile())
 *         .build();
 *
 * // Narrow seeds
 * TestContainer.builder()
 *         .beans(SelfInjectingBean.class)
 *         .build();
 * }</pre>
 */
@Internal
public final class TestContainer {

    private TestContainer() {}

    public static BeanContainer build() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Class<?> testClass;
        private Engine engine = Engine.RUNTIME;
        private Class<?>[] beans = new Class<?>[0];
        private List<MockedBean> mocks = List.of();
        private Map<String, Object> overrides = Map.of();
        private JandexIndexLoader.LoadedIndex productionIndex;
        private java.util.Map<java.nio.file.Path, org.jboss.jandex.Index> testIndexCache;

        /**
         * DTO injection from the test lifecycle: the shared production index and the per-directory
         * test-index cache, so a full classpath sweep / test-classes walk happens once per JVM run
         * rather than once per universe. Package-private — TestContainer is {@code @Internal}.
         */
        Builder withIndexes(
                JandexIndexLoader.LoadedIndex productionIndex,
                java.util.Map<java.nio.file.Path, org.jboss.jandex.Index> testIndexCache) {
            this.productionIndex = productionIndex;
            this.testIndexCache = testIndexCache;
            return this;
        }

        public Builder testClass(Class<?> c) {
            testClass = c;
            return this;
        }

        public Builder engine(Engine e) {
            engine = e;
            return this;
        }

        public Builder beans(Class<?>... b) {
            beans = b;
            return this;
        }

        public Builder profile(SummerTestProfile p) {
            if (p != null) {
                overrides = p.configOverrides();
            }
            return this;
        }

        /** Not for end-user tests. */
        @Internal
        public Builder mocks(List<MockedBean> m) {
            mocks = m;
            return this;
        }

        /** Not for end-user tests. */
        @Internal
        public Builder overrides(Map<String, Object> o) {
            overrides = o;
            return this;
        }

        public BeanContainer build() {
            var deployment =
                    beans.length > 0
                            ? BeanDeployment.forNarrow(NarrowIndexBuilder.build(beans))
                            : testUniverse(testClass, productionIndex, testIndexCache);
            String cacheKey =
                    beans.length > 0
                            ? narrowCacheKey(beans, mocks, overrides)
                            : (testClass != null
                                    ? AotKey.forTest(testClass, overrides).cacheKey()
                                    : AotKey.forUniverse().cacheKey());
            String className =
                    beans.length > 0
                            ? AotKey.forNarrow(narrowCacheKey(beans, mocks, overrides)).className()
                            : (testClass != null
                                    ? AotKey.forTest(testClass, overrides).className()
                                    : AotKey.forUniverse().className());
            // AOT codegen parameters travel on the deployment (AOT-specific; the runtime
            // engine ignores them), keeping the shared ContainerEngine.build signature
            // engine-agnostic.
            deployment.withCodegen(cacheKey, className);
            return ContainerEngines.forEngine(engine)
                    .build(deployment, mocks.toArray(new MockedBean[0]), overrides);
        }
    }

    public static boolean isComponent(Class<?> clazz) {
        if (clazz.isAnnotationPresent(Component.class)) return true;
        for (Annotation ann : clazz.getAnnotations()) {
            if (ann.annotationType().isAnnotationPresent(Component.class)) return true;
        }
        return false;
    }

    // ── internal pipeline ────────────────────────────────────────────

    private static String narrowCacheKey(
            Class<?>[] seeds, List<MockedBean> mocks, Map<String, Object> overrides) {
        String seedSig =
                Arrays.stream(seeds).map(Class::getName).sorted().collect(Collectors.joining(","));
        String mockSig =
                mocks.stream()
                        .map(MockedBean::targetTypeName)
                        .sorted()
                        .collect(Collectors.joining(","));
        String overrideSig =
                overrides.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .map(e -> e.getKey() + "=" + e.getValue())
                        .collect(Collectors.joining(","));
        // Overrides (@TestProfile content) change the baked config-impl literals, so they must
        // distinguish the generated graph — same contract as AotKey.forTest.
        return seedSig + "|mocks=" + mockSig + "|overrides=" + overrideSig;
    }

    private static BeanDeployment testUniverse(
            Class<?> testClass,
            JandexIndexLoader.LoadedIndex productionIndex,
            java.util.Map<java.nio.file.Path, org.jboss.jandex.Index> testIndexCache) {
        // Direct (non-lifecycle) callers may not supply the DTOs — fall back to loading, same as
        // before (one load per call). The @SummerTest path always supplies them via
        // Builder.withIndexes, so a full classpath sweep happens once per JVM run.
        JandexIndexLoader.LoadedIndex prod =
                productionIndex != null ? productionIndex : JandexIndexLoader.productionIndex();
        Class<?> resolved = (testClass != null) ? testClass : TestClassIndexer.inferTestClass();
        IndexView testIndex =
                (resolved != null)
                        ? TestClassIndexer.indexTestClasses(
                                resolved,
                                testIndexCache != null ? testIndexCache : new java.util.HashMap<>())
                        : org.jboss.jandex.CompositeIndex.create(List.of());
        var classToArchive = new java.util.HashMap<>(prod.classToArchive());
        for (String type : testIndex.getKnownClasses().stream().map(Object::toString).toList()) {
            classToArchive.put(type, "test");
        }
        var archiveIndexes = new java.util.HashMap<>(prod.archiveIndexes());
        archiveIndexes.put("test", testIndex);
        return BeanDeployment.forTestUniverse(prod.index(), classToArchive, archiveIndexes);
    }
}
