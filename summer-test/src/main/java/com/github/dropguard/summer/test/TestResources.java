package com.github.dropguard.summer.test;

import com.github.dropguard.summer.core.Internal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Global cache of started {@link TestResourceManager} instances.
 *
 * <p>Each unique (resource class, initArgs) pair is instantiated once and started at most once,
 * sorted by {@link TestResourceManager#order()} (later resources win on key overlap). Properties
 * from {@code start()} flow into the container build as highest-priority config overrides; started
 * instances also serve {@link TestResourceManager#inject(TestInjector)} after a test instance is
 * created.
 */
@Internal
public final class TestResources {

    private static final Map<ResourceKey, Entry> CACHE = new ConcurrentHashMap<>();

    private TestResources() {}

    static Map<String, Object> resolveProperties(Class<?> testClass) {
        com.github.dropguard.summer.test.annotation.TestResource[] annotations =
                collectAnnotations(testClass);
        if (annotations.length == 0) {
            return Map.of();
        }
        List<Entry> started = new ArrayList<>();
        for (com.github.dropguard.summer.test.annotation.TestResource ann : annotations) {
            started.add(forKey(new ResourceKey(ann.value(), parseInitArgs(ann.initArgs()))));
        }
        // Deterministic merge: later order() wins on key overlap (matches the start sequence).
        Map<String, Object> merged = new LinkedHashMap<>();
        started.stream()
                .sorted(Comparator.comparingInt(e -> e.instance.order()))
                .forEach(e -> merged.putAll(e.properties));
        return Map.copyOf(merged);
    }

    /**
     * Runs {@link TestResourceManager#inject(TestInjector)} for every started resource on the
     * class.
     */
    static void injectInto(Class<?> testClass, Object testInstance) {
        com.github.dropguard.summer.test.annotation.TestResource[] annotations =
                collectAnnotations(testClass);
        if (annotations.length == 0) {
            return;
        }
        TestInjectorImpl injector = new TestInjectorImpl(testInstance);
        // Same order() semantics as the property merge below: a later-order resource's field
        // injection wins on overlap, so the injected values and the container-build overrides
        // agree instead of splitting (declaration-order injection vs order()-sorted merge).
        java.util.Arrays.stream(annotations)
                .map(ann -> forKey(new ResourceKey(ann.value(), parseInitArgs(ann.initArgs()))))
                .sorted(java.util.Comparator.comparingInt(e -> e.instance.order()))
                .forEach(e -> e.instance.inject(injector));
    }

    private static Entry forKey(ResourceKey key) {
        return CACHE.computeIfAbsent(key, TestResources::startNew);
    }

    private static Entry startNew(ResourceKey key) {
        try {
            TestResourceManager instance = key.clazz.getDeclaredConstructor().newInstance();
            instance.init(key.initArgs);
            Map<String, String> props = instance.start();
            return new Entry(instance, Map.copyOf(props));
        } catch (Exception e) {
            throw new TestResourceStartupException(
                    "Failed to start TestResource " + key.clazz.getSimpleName(), e);
        }
    }

    private static Map<String, String> parseInitArgs(String[] initArgs) {
        Map<String, String> parsed = new LinkedHashMap<>();
        for (String arg : initArgs) {
            int idx = arg.indexOf('=');
            if (idx < 0) {
                throw new IllegalArgumentException(
                        "TestResource initArg must be key=value: " + arg);
            }
            parsed.put(arg.substring(0, idx).trim(), arg.substring(idx + 1).trim());
        }
        return Map.copyOf(parsed);
    }

    private static com.github.dropguard.summer.test.annotation.TestResource[] collectAnnotations(
            Class<?> testClass) {
        com.github.dropguard.summer.test.annotation.TestResource.List list =
                testClass.getAnnotation(
                        com.github.dropguard.summer.test.annotation.TestResource.List.class);
        if (list != null) {
            return list.value();
        }
        com.github.dropguard.summer.test.annotation.TestResource single =
                testClass.getAnnotation(
                        com.github.dropguard.summer.test.annotation.TestResource.class);
        return single != null
                ? new com.github.dropguard.summer.test.annotation.TestResource[] {single}
                : new com.github.dropguard.summer.test.annotation.TestResource[0];
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

    private record ResourceKey(
            Class<? extends TestResourceManager> clazz, Map<String, String> initArgs) {}

    private record Entry(TestResourceManager instance, Map<String, String> properties) {}

    private static final class TestInjectorImpl implements TestResourceManager.TestInjector {
        private final Object testInstance;

        TestInjectorImpl(Object testInstance) {
            this.testInstance = testInstance;
        }

        @Override
        public void injectIntoFields(Object value, Class<?> fieldType) {
            java.lang.reflect.Field[] fields = testInstance.getClass().getDeclaredFields();
            for (java.lang.reflect.Field field : fields) {
                if (fieldType.isAssignableFrom(field.getType())) {
                    set(field, value);
                }
            }
        }

        @Override
        public void injectIntoField(Object value, String fieldName, Class<?> fieldType) {
            try {
                java.lang.reflect.Field field = testInstance.getClass().getDeclaredField(fieldName);
                if (fieldType.isAssignableFrom(field.getType())) {
                    set(field, value);
                }
            } catch (NoSuchFieldException ignored) {
                // The field is optional ("when declared") — no field, no injection.
            }
        }

        private void set(java.lang.reflect.Field field, Object value) {
            try {
                field.setAccessible(true);
                field.set(testInstance, value);
            } catch (IllegalAccessException e) {
                throw new RuntimeException("Failed to inject field " + field.getName(), e);
            }
        }
    }
}
