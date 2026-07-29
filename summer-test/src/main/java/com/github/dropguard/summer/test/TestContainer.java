package com.github.dropguard.summer.test;

import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.core.Component;
import com.github.dropguard.summer.core.ContainerEngines;
import com.github.dropguard.summer.core.Engine;
import com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.core.bean.MockedBean;
import com.github.dropguard.summer.runtime.JandexIndexLoader;
import com.github.dropguard.summer.runtime.TestClassIndexer;
import com.github.dropguard.summer.test.internal.AotKey;
import com.github.dropguard.summer.test.internal.NarrowIndexBuilder;
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
                            ? com.github.dropguard.summer.core.bean.BeanDeployment.forNarrow(
                                    NarrowIndexBuilder.build(beans))
                            : testUniverse(testClass);
            return ContainerEngines.forEngine(engine)
                    .build(
                            deployment,
                            mocks.toArray(new MockedBean[0]),
                            overrides,
                            beans.length > 0
                                    ? narrowCacheKey(beans, mocks)
                                    : AotKey.forTest(testClass, overrides).cacheKey(),
                            beans.length > 0
                                    ? AotKey.forNarrow(narrowCacheKey(beans, mocks)).className()
                                    : AotKey.forTest(testClass, overrides).className());
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

    private static String narrowCacheKey(Class<?>[] seeds, List<MockedBean> mocks) {
        String seedSig =
                Arrays.stream(seeds).map(Class::getName).sorted().collect(Collectors.joining(","));
        String mockSig =
                mocks.stream()
                        .map(MockedBean::targetTypeName)
                        .sorted()
                        .collect(Collectors.joining(","));
        return seedSig + "|mocks=" + mockSig;
    }

    private static com.github.dropguard.summer.core.bean.BeanDeployment testUniverse(
            Class<?> testClass) {
        JandexIndexLoader.LoadedIndex prod = JandexIndexLoader.productionIndex();
        Class<?> resolved = (testClass != null) ? testClass : TestClassIndexer.inferTestClass();
        IndexView testIndex =
                (resolved != null)
                        ? TestClassIndexer.indexTestClasses(resolved)
                        : org.jboss.jandex.CompositeIndex.create(List.of());
        var classToArchive = new java.util.HashMap<>(prod.classToArchive());
        for (String type : testIndex.getKnownClasses().stream().map(Object::toString).toList()) {
            classToArchive.put(type, "test");
        }
        var archiveIndexes = new java.util.HashMap<>(prod.archiveIndexes());
        archiveIndexes.put("test", testIndex);
        return com.github.dropguard.summer.core.bean.BeanDeployment.forTestUniverse(
                prod.index(), classToArchive, archiveIndexes);
    }
}
