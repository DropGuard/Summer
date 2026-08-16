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
 * public class PostgresTestResource implements TestResourceManager {
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
public interface TestResourceManager {

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

    /**
     * Called before {@link #start()} with the merged properties of every resource that started
     * earlier (lower {@link #order()}), so a resource can consume the outputs of its predecessors —
     * e.g. a seed resource reading the JDBC URL produced by the Postgres resource. Mirrors Quarkus'
     * {@code DevServicesContext.ContextAware}. The default is a no-op for resources that need no
     * shared context.
     *
     * <p>Note: this is <em>not</em> a substitute for {@link #start()} returning config overrides —
     * those still flow into the DI container. {@code setContext} is the read-side channel for
     * cross-resource coordination only.
     */
    default void setContext(Map<String, String> sharedProperties) {}

    /** Field-injection channel for {@link #inject(TestInjector)}. */
    interface TestInjector {
        /** Injects the value into every declared field of the given type on the test instance. */
        void injectIntoFields(Object value, Class<?> fieldType);

        /**
         * Injects the value into the named field (when declared and of the given type). The
         * targeted form: a resource that owns one field (e.g. the JDBC URL) must not clobber every
         * field of a shared type on the test instance.
         */
        void injectIntoField(Object value, String fieldName, Class<?> fieldType);
    }
}
