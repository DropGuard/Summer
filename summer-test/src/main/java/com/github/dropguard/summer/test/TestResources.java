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
 * sorted by {@link TestResourceManager#order()} (later resources start later and win on key
 * overlap). Properties from {@code start()} flow into the container build as highest-priority
 * config overrides; started instances also serve {@link TestResourceManager#inject(TestInjector)}
 * after a test instance is created.
 *
 * <p>Resources start in {@code order()} sequence; the merged properties of every earlier resource
 * are handed to the next via {@link TestResourceManager#setContext(Map)} (the Quarkus {@code
 * DevServicesContext.ContextAware} model), so a later resource can consume an earlier one's outputs
 * (e.g. a seed resource reading the JDBC URL produced by the Postgres resource).
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
        // Start every resource exactly once, in order() sequence, feeding each one the merged
        // properties of its predecessors via setContext so a later resource can read an earlier
        // one's outputs (e.g. a seed resource reading the Postgres resource's JDBC URL).
        List<Entry> started = startAll(annotations);
        // Deterministic merge: later order() wins on key overlap (matches the start sequence).
        Map<String, Object> merged = new LinkedHashMap<>();
        started.stream()
                .sorted(Comparator.comparingInt(e -> e.instance().order()))
                .forEach(e -> merged.putAll(e.properties()));
        return Map.copyOf(merged);
    }

    /**
     * Runs {@link TestResourceManager#inject(TestInjector)} for every started resource on the
     * class. Ensures resources are started (idempotently) before injecting.
     */
    static void injectInto(Class<?> testClass, Object testInstance) {
        com.github.dropguard.summer.test.annotation.TestResource[] annotations =
                collectAnnotations(testClass);
        if (annotations.length == 0) {
            return;
        }
        TestInjectorImpl injector = new TestInjectorImpl(testInstance);
        startAll(annotations).forEach(e -> e.instance().inject(injector));
    }

    /** Package-private seam for contract tests: collect + start for one test class. */
    static List<Entry> startAllForClass(Class<?> testClass) {
        return startAll(collectAnnotations(testClass));
    }

    /** Instantiates + starts each unique resource exactly once, in order(), sharing properties. */
    private static synchronized List<Entry> startAll(
            com.github.dropguard.summer.test.annotation.TestResource[] annotations) {
        // Deduplicate by (class, initArgs); each unique resource starts once (cached across test
        // classes, so a shared resource like Postgres must not restart per test class).
        List<ResourceKey> keys = new ArrayList<>();
        for (com.github.dropguard.summer.test.annotation.TestResource ann : annotations) {
            ResourceKey key = new ResourceKey(ann.value(), parseInitArgs(ann.initArgs()));
            if (!keys.contains(key)) {
                keys.add(key);
            }
        }
        // Key and entry travel as ONE pair through the order() sort — writing back via sorted
        // positions against declaration-order keys crossed cache mappings whenever the two
        // orders differed, poisoning later classes with another resource's instance.
        List<KeyedEntry> keyed = new ArrayList<>();
        for (ResourceKey key : keys) {
            Entry cached = CACHE.get(key);
            keyed.add(
                    new KeyedEntry(
                            key,
                            cached != null
                                    ? cached
                                    : CACHE.computeIfAbsent(key, TestResources::instantiate)));
        }
        keyed.sort(Comparator.comparingInt(k -> k.entry().instance().order()));
        // Start the not-yet-started resources in order, feeding each the merged properties of its
        // predecessors (both freshly-started and previously-cached ones).
        Map<String, String> shared = new LinkedHashMap<>();
        List<Entry> result = new ArrayList<>();
        for (KeyedEntry keyedEntry : keyed) {
            Entry entry = keyedEntry.entry();
            if (entry.started()) {
                shared.putAll(entry.properties());
                result.add(entry);
            } else {
                entry.instance().setContext(shared);
                Map<String, String> props = start(entry);
                shared.putAll(props);
                Entry startedEntry = new Entry(entry.instance(), props, true);
                CACHE.put(keyedEntry.key(), startedEntry);
                result.add(startedEntry);
            }
        }
        return result;
    }

    private record KeyedEntry(ResourceKey key, Entry entry) {}

    /** Instantiates a resource without starting it (order() is needed before start). */
    private static Entry instantiate(ResourceKey key) {
        try {
            TestResourceManager instance = key.clazz.getDeclaredConstructor().newInstance();
            instance.init(key.initArgs);
            return new Entry(instance, Map.of(), false);
        } catch (Exception e) {
            throw new TestResourceStartupException(
                    "Failed to instantiate TestResource " + key.clazz.getSimpleName(), e);
        }
    }

    /** Runs {@link TestResourceManager#start()} and captures its config properties. */
    private static Map<String, String> start(Entry entry) {
        try {
            return Map.copyOf(entry.instance().start());
        } catch (Exception e) {
            throw new TestResourceStartupException(
                    "Failed to start TestResource " + entry.instance().getClass().getSimpleName(),
                    e);
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
                entry.instance().stop();
            } catch (Exception e) {
                // Cleanup failure must not mask the original test flow, but it must also not
                // vanish: a leaked container holds ports and memory on this machine.
                org.slf4j.LoggerFactory.getLogger(TestResources.class)
                        .warn("Failed to stop TestResource {}", entry.instance(), e);
            }
        }
        CACHE.clear();
    }

    private record ResourceKey(
            Class<? extends TestResourceManager> clazz, Map<String, String> initArgs) {}

    record Entry(TestResourceManager instance, Map<String, String> properties, boolean started) {}

    private static final class TestInjectorImpl implements TestResourceManager.TestInjector {
        private final Object testInstance;

        TestInjectorImpl(Object testInstance) {
            this.testInstance = testInstance;
        }

        @Override
        public void injectIntoFields(Object value, Class<?> fieldType) {
            for (java.lang.reflect.Field field : fieldsInHierarchy()) {
                if (fieldType.isAssignableFrom(field.getType())) {
                    set(field, value);
                }
            }
        }

        @Override
        public void injectIntoField(Object value, String fieldName, Class<?> fieldType) {
            for (java.lang.reflect.Field field : fieldsInHierarchy()) {
                if (field.getName().equals(fieldName)
                        && fieldType.isAssignableFrom(field.getType())) {
                    set(field, value);
                    return;
                }
            }
        }

        /** All declared fields across the test class and its superclasses. */
        private java.lang.reflect.Field[] fieldsInHierarchy() {
            java.util.List<java.lang.reflect.Field> all = new java.util.ArrayList<>();
            Class<?> current = testInstance.getClass();
            while (current != null && current != Object.class) {
                java.util.Collections.addAll(all, current.getDeclaredFields());
                current = current.getSuperclass();
            }
            return all.toArray(new java.lang.reflect.Field[0]);
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
