package com.github.dropguard.summer.test;

import com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.test.TestResource;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Global cache of started {@link TestResource} instances.
 *
 * <p>Each unique resource class is instantiated once and started at most once. Properties from
 * {@code start()} flow into the container build as highest-priority config overrides.
 */
@Internal
public final class TestResources {

    private static final Map<Class<? extends TestResource>, Entry> CACHE =
            new ConcurrentHashMap<>();

    private TestResources() {}

    static Map<String, Object> resolveProperties(Class<?> testClass) {
        com.github.dropguard.summer.test.annotation.TestResource[] annotations =
                collectAnnotations(testClass);
        if (annotations.length == 0) {
            return Map.of();
        }
        Map<String, Object> merged = new LinkedHashMap<>();
        for (TestResource ann : annotations) {
            merged.putAll(forClass(ann.value()));
        }
        return Map.copyOf(merged);
    }

    private static Map<String, String> forClass(Class<? extends TestResource> clazz) {
        return CACHE.computeIfAbsent(clazz, TestResources::startNew).properties;
    }

    private static Entry startNew(Class<? extends TestResource> clazz) {
        try {
            TestResource instance = clazz.getDeclaredConstructor().newInstance();
            Map<String, String> props = instance.start();
            return new Entry(instance, Map.copyOf(props));
        } catch (Exception e) {
            throw new RuntimeException("Failed to start TestResource " + clazz.getSimpleName(), e);
        }
    }

    private static TestResource[] collectAnnotations(Class<?> testClass) {
        TestResource.List list = testClass.getAnnotation(TestResource.List.class);
        if (list != null) {
            return list.value();
        }
        TestResource single = testClass.getAnnotation(TestResource.class);
        return single != null ? new TestResource[] {single} : new TestResource[0];
    }

    static void shutdown() {
        for (Entry entry : CACHE.values()) {
            try {
                entry.instance.stop();
            } catch (Exception ignored) {
            }
        }
        CACHE.clear();
    }

    private record Entry(TestResource instance, Map<String, String> properties) {}
}
