package com.github.dropguard.summer.test;

import java.util.Map;

/**
 * Manages an external resource (database, broker, container) for the lifetime of a test suite —
 * Quarkus' {@code QuarkusTestResource} model.
 *
 * <p>{@link #start()} returns configuration properties that are fed into the DI container as config
 * overrides, with higher priority than any other config source. {@link #stop()} tears the resource
 * down on JVM exit.
 *
 * <p>Declared on a test class via {@code @TestResource(MyPostgres.class)}. Framework-owned — never
 * called by user code directly.
 *
 * <pre>{@code
 * public class PostgresTestResource implements TestResource {
 *     private PostgreSQLContainer&lt;?&gt; pg;
 *
 *     public Map&lt;String, String&gt; start() {
 *         pg = new PostgreSQLContainer&lt;&gt;("postgres:15");
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
     * before any bean is constructed.
     */
    Map<String, String> start() throws Exception;

    /** Tears down the resource. Called when the JVM exits. */
    void stop();
}
