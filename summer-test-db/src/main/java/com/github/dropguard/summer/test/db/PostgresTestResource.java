package com.github.dropguard.summer.test.db;

import com.github.dropguard.summer.test.TestResource;
import java.util.Map;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Framework-owned Postgres resource for {@code @TestResource}-based tests — the concrete form of
 * the SPI's javadoc example. Returns the {@code datasource.*} keys (the dotted YAML path — {@code
 * ConfigBinder.BindingContext} contract) that feed the DI container's config overrides.
 *
 * <p>Parameterized via {@code @TestResource(value = PostgresTestResource.class, initArgs =
 * "image=postgres:16-alpine")} (default {@code postgres:16-alpine}); injects the JDBC URL into the
 * test's {@code String jdbcUrl} field when declared.
 */
public class PostgresTestResource implements TestResource {

    private static final String DEFAULT_IMAGE = "postgres:16-alpine";

    private PostgreSQLContainer<?> pg;
    private String jdbcUrl;

    @Override
    public void init(Map<String, String> initArgs) {
        pg = new PostgreSQLContainer<>(initArgs.getOrDefault("image", DEFAULT_IMAGE));
    }

    @Override
    public Map<String, String> start() {
        pg.start();
        jdbcUrl = pg.getJdbcUrl();
        return Map.of(
                "datasource.url",
                jdbcUrl,
                "datasource.username",
                pg.getUsername(),
                "datasource.password",
                pg.getPassword(),
                "datasource.driverClassName",
                "org.postgresql.Driver");
    }

    @Override
    public void inject(TestInjector injector) {
        injector.injectIntoFields(jdbcUrl, String.class);
    }

    @Override
    public void stop() {
        if (pg != null) {
            pg.stop();
        }
    }
}
