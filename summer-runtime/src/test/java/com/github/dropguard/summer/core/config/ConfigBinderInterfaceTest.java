package com.github.dropguard.summer.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.dropguard.summer.core.exception.MissingFieldException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ConfigBinder#bind} on the Quarkus-style {@code @ConfigMapping} interface
 * model: method-name keys, {@link WithDefault}, {@link WithName}, nested interfaces,
 * collections/enums, and {@code @TestProfile} overrides applied through the shared {@link
 * BindingContext}.
 *
 * <p>Each case feeds values entirely through the {@link BindingContext} (defaults + overrides) so
 * the tests are isolated from any {@code application.yml} on the classpath.
 */
class ConfigBinderInterfaceTest {

    @ConfigMapping(prefix = "sample")
    interface SampleConfig {
        String host();

        @WithDefault("8080")
        int port();
    }

    @Test
    void bindsInterfaceFromDefaults() {
        Map<String, Object> defaults = Map.of("host", "localhost", "port", 8080);
        SampleConfig cfg =
                new ConfigBinder().bind(
                        ConfigBinder.BindingContext.of(defaults, Map.of()),
                        "sample",
                        SampleConfig.class);
        assertEquals("localhost", cfg.host());
        assertEquals(8080, cfg.port());
    }

    @Test
    void withDefaultAppliesWhenUnbound() {
        Map<String, Object> defaults = Map.of("host", "localhost");
        SampleConfig cfg =
                new ConfigBinder().bind(
                        ConfigBinder.BindingContext.of(defaults, Map.of()),
                        "sample",
                        SampleConfig.class);
        assertEquals("localhost", cfg.host());
        assertEquals(8080, cfg.port()); // from @WithDefault
    }

    @Test
    void missingRequiredKeyThrows() {
        Map<String, Object> empty = Map.of();
        // Interface binding is lazy: the proxy resolves fields on access, so the
        // missing-key error surfaces when the accessor is invoked, not at bind time.
        SampleConfig cfg =
                new ConfigBinder().bind(
                        ConfigBinder.BindingContext.of(empty, Map.of()),
                        "sample",
                        SampleConfig.class);
        MissingFieldException ex = assertThrows(MissingFieldException.class, cfg::host);
        assertTrue(ex.getMessage().contains("host"));
    }

    // --- @WithName: explicit key rename (Go json:"x" equivalent) ---

    @ConfigMapping(prefix = "conn")
    interface ConnConfig {
        @WithName("max-conn")
        int maxConn();

        String host();
    }

    @Test
    void withNameResolvesExplicitKey() {
        // The defaults map is keyed by the resolved key (camelCased @WithName value).
        Map<String, Object> defaults = Map.of("maxConn", 5, "host", "h");
        ConnConfig cfg =
                new ConfigBinder().bind(
                        ConfigBinder.BindingContext.of(defaults, Map.of()),
                        "conn",
                        ConnConfig.class);
        assertEquals(5, cfg.maxConn());
        assertEquals("h", cfg.host());
    }

    // --- nested interface (prefixes chosen to avoid any application.yml section)
    // ---

    @ConfigMapping(prefix = "myserver")
    interface ServerConfig {
        String host();

        DatabaseConfig database();
    }

    @ConfigMapping(prefix = "myserver.database")
    interface DatabaseConfig {
        String url();

        @WithDefault("postgres")
        String driver();
    }

    @Test
    void bindsNestedInterface() {
        // Immutable nested default value — bindSection no longer mutates inputs in
        // place, so Map.of(...) nested values are safe (no LinkedHashMap walkaround).
        Map<String, Object> db = Map.of("url", "jdbc:x");
        Map<String, Object> defaults = Map.of("host", "h", "database", db);
        ServerConfig cfg =
                new ConfigBinder().bind(
                        ConfigBinder.BindingContext.of(defaults, Map.of()),
                        "myserver",
                        ServerConfig.class);
        assertEquals("h", cfg.host());
        assertEquals("jdbc:x", cfg.database().url());
        assertEquals("postgres", cfg.database().driver()); // nested @WithDefault
    }

    // --- collection + enum ---

    enum RouterType {
        RADIX_TREE,
        LINEAR
    }

    @ConfigMapping(prefix = "web")
    interface WebConfig {
        @WithDefault("RADIX_TREE")
        RouterType routerType();

        @WithDefault("")
        List<String> allowedOrigins();
    }

    @Test
    void bindsEnumAndList() {
        Map<String, Object> defaults =
                Map.of("routerType", "LINEAR", "allowedOrigins", List.of("https://a", "https://b"));
        WebConfig cfg =
                new ConfigBinder().bind(
                        ConfigBinder.BindingContext.of(defaults, Map.of()), "web", WebConfig.class);
        assertEquals(RouterType.LINEAR, cfg.routerType());
        assertEquals(List.of("https://a", "https://b"), cfg.allowedOrigins());
    }

    // --- @TestProfile override (dotted path, wins over defaults) ---

    @Test
    void profileOverrideWinsOverDefault() {
        Map<String, Object> defaults = Map.of("host", "localhost", "port", 8080);
        Map<String, Object> overrides = Map.of("sample.port", 9090);
        SampleConfig cfg =
                new ConfigBinder().bind(
                        ConfigBinder.BindingContext.of(defaults, overrides),
                        "sample",
                        SampleConfig.class);
        assertEquals("localhost", cfg.host());
        assertEquals(9090, cfg.port()); // override wins over @WithDefault/default
    }
}
