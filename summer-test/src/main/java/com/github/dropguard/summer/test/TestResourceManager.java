package com.github.dropguard.summer.test;

import java.util.Map;

/**
 * Manages an external resource (database, broker, container) for the lifetime of a test suite —
 * Quarkus' {@code QuarkusTestResourceLifecycleManager} model.
 *
 * <p>{@link #start()} returns configuration properties that are fed into the DI container as config
 * overrides, with higher priority than any other config source. {@link #stop()} tears the resource
 * down on JVM exit.
 *
 * <p>Declared on a test class via {@code @TestResource(MyPostgres.class)}. Framework-owned — never
 * called by user code directly.
 *
 * <p>Lifecycle (in order): {@link #init(Map)} with the {@code @TestResource} initArgs → {@link
 * #start()} → (per test instance, after constructor injection) {@link #inject(TestInjector)} →
 * {@link #stop()} at JVM exit. Resources with a higher {@link #order()} start later.
 *
 * <pre>{@code
 * public class PostgresTestResource implements TestResource {
 *     private PostgreSQLContainer<?> pg;
 *
 *     public Map<String, String> start() {
 *         pg = new PostgreSQLContainer<>("postgres:15");
 *         pg.start();
 *         return Map.of("datasource.url", pg.getJdbcUrl());
 *     }
 *
 *     public void stop() {
 *         if (pg != null) pg.stop();
 *     }
 * }
 * }</pre>
 */
public interface TestResource {

    /**
     * Starts the external resource and returns config properties injected into the DI container
     * before any bean is constructed. Keys are the dotted YAML path (the {@code
     * ConfigBinder.BindingContext} contract) — an env-style key silently falls back to
     * {@code @WithDefault} (the original RedisTestResource bug).
     */
    Map<String, String> start() throws Exception;

    /** Tears down the resource. Called when the JVM exits. */
    void stop();

    /**
     * Called before {@link #start()} with the {@code @TestResource(initArgs = ...)} values, so a
     * resource is parameterized per declaration (e.g. the container image) instead of hardcoding
     * them.
     */
    default void init(Map<String, String> initArgs) {}

    /**
     * Called after the test instance's constructor injection, so a resource can push its values
     * (mapped ports, URLs, clients) into the test class's fields — the second channel beside the
     * config overrides.
     */
    default void inject(TestInjector testInjector) {}

    /**
     * Execution order among multiple resources on one test class; larger runs later. The {@code
     * start()} property maps are merged in this order (later resources win on key overlap).
     */
    default int order() {
        return 0;
    }

    /** Field-injection channel for {@link #inject(TestInjector)}. */
    interface TestInjector {
        /** Injects the value into every declared field of the given type on the test instance. */
        void injectIntoFields(Object value, Class<?> fieldType);
    }
}
