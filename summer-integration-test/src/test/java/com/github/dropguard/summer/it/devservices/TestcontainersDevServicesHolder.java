package com.github.dropguard.summer.test.devservices;

import com.github.dropguard.summer.test.internal.DevServicesHolder;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.util.Map;
import javax.sql.DataSource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Testcontainers-backed dev-services for the framework's own integration tests.
 *
 * <p>This is the {@code summer-test} framework's well-known holder implementation (located by
 * {@code SummerTestLifecycle} via reflection), kept in the {@code summer-integration-test} module
 * so {@code summer-test} itself never depends on Testcontainers or Docker. The holder starts the
 * real infrastructure the framework IT needs — a single Postgres and a single Redis — for the whole
 * test run, and hands out one shared {@link DataSource} pool and one Redis URI. That eliminates the
 * per-{@code @SummerTest} pool proliferation that previously exhausted Postgres' {@code
 * max_connections}.
 *
 * <p>This is an <b>internal</b> convenience: only framework-owned integration tests (this module,
 * and any other internal test module) use it. Demos and external users must NOT — they use the
 * public path (start Testcontainers directly and set {@code
 * com.github.dropguard.summer.test.datasource.url} / {@code
 * com.github.dropguard.summer.redis.uri}), exactly like {@code RedisIntegrationIT} does.
 */
public class TestcontainersDevServicesHolder implements DevServicesHolder {

    /** URL form the test DB config recognizes as "use the shared dev-services pool". */
    private static final String SHARED_URL_SCHEME = "summer:devservices:postgres";

    private static final int REDIS_PORT = 6379;

    private PostgreSQLContainer<?> postgres;
    private GenericContainer<?> redis;
    private HikariDataSource sharedPool;
    private volatile boolean started;

    @Override
    public synchronized ConnectionDescriptor start(Map<String, String> requestedEnvironment) {
        if (started) {
            return currentDescriptor();
        }
        String dbName = requestedEnvironment.getOrDefault("database", "summer_it");
        postgres =
                new PostgreSQLContainer<>(DockerImageName.parse("postgres:15"))
                        .withDatabaseName(dbName)
                        .withUsername("test")
                        .withPassword("test");
        postgres.start();

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(postgres.getJdbcUrl());
        config.setUsername("test");
        config.setPassword("test");
        config.setDriverClassName("org.postgresql.Driver");
        // One pool for the entire JVM. Every @SummerTest that wants the real DB reuses
        // it; we deliberately keep the size small and rely on the shared lifetime.
        config.setMaximumPoolSize(4);
        sharedPool = new HikariDataSource(config);

        redis =
                new GenericContainer<>(DockerImageName.parse("redis:7"))
                        .withExposedPorts(REDIS_PORT);
        redis.start();

        started = true;
        return currentDescriptor();
    }

    @Override
    public synchronized void stop() {
        if (sharedPool != null) {
            try {
                sharedPool.close();
            } catch (Exception ignored) {
            }
            sharedPool = null;
        }
        if (postgres != null) {
            try {
                postgres.stop();
            } catch (Exception ignored) {
            }
            postgres = null;
        }
        if (redis != null) {
            try {
                redis.stop();
            } catch (Exception ignored) {
            }
            redis = null;
        }
        started = false;
    }

    @Override
    public boolean owns(String url) {
        return url != null && url.startsWith(SHARED_URL_SCHEME);
    }

    @Override
    public DataSource sharedDataSource(String url) {
        if (!owns(url)) {
            throw new IllegalArgumentException("URL not owned by dev-services: " + url);
        }
        if (sharedPool == null) {
            throw new IllegalStateException("Dev-services pool requested before start()");
        }
        return sharedPool;
    }

    private ConnectionDescriptor currentDescriptor() {
        // The descriptor URL is the shared scheme; the real JDBC coordinates live
        // inside sharedDataSource(). The test DB config routes through owns()+that.
        return new ConnectionDescriptor(SHARED_URL_SCHEME, "test", "test", "org.postgresql.Driver");
    }

    /**
     * Redis URI for the shared container (redis://host:port), for {@code
     * com.github.dropguard.summer.redis.uri}.
     */
    public String redisUri() {
        if (redis == null) {
            throw new IllegalStateException("Dev-services not started");
        }
        return "redis://" + redis.getHost() + ":" + redis.getMappedPort(REDIS_PORT);
    }

    /** Convenience the integration test can use to run schema/seed DDL. */
    public Connection openAdminConnection() throws Exception {
        if (postgres == null) {
            throw new IllegalStateException("Dev-services not started");
        }
        return postgres.createConnection("");
    }
}
