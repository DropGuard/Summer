package com.github.dropguard.summer.it;

import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.test.devservices.TestcontainersDevServicesHolder;
import com.github.dropguard.summer.test.internal.SummerTestLifecycle;
import java.sql.Connection;
import java.sql.Statement;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

/**
 * Shared bootstrap for the framework's real-stack integration tests.
 *
 * <p>Uses the <b>internal</b> {@code DevServicesHolder} convenience (via {@code
 * SummerTestLifecycle}) to start one shared Postgres + Redis for the JVM and point the test config
 * at it. This is framework-internal — demos must not depend on {@code
 * com.github.dropguard.summer.test.internal}; they start Testcontainers directly and set {@code
 * com.github.dropguard.summer.test.datasource.url} / {@code com.github.dropguard.summer.redis.uri}
 * themselves (see {@code RedisIntegrationIT}).
 */
abstract class AbstractFrameworkIT {

    protected static BeanContainer context;

    @BeforeAll
    static void startEnvironment() throws Exception {
        SummerTestLifecycle ctx = SummerTestLifecycle.instance();
        ctx.ensureDevServices(java.util.Map.of("database", "summer_it"));
        TestcontainersDevServicesHolder holder =
                (TestcontainersDevServicesHolder) ctx.devServices();

        try (Connection conn = holder.openAdminConnection()) {
            try (Statement st = conn.createStatement()) {
                st.execute("DROP TABLE IF EXISTS greetings");
                st.execute("CREATE TABLE greetings (id BIGINT PRIMARY KEY, text VARCHAR(255))");
            }
        }

        System.setProperty(
                "com.github.dropguard.summer.test.datasource.url", "summer:devservices:postgres");
        System.setProperty("com.github.dropguard.summer.redis.uri", holder.redisUri());

        context = com.github.dropguard.summer.test.Testing.buildForTest(AbstractFrameworkIT.class);
    }

    @AfterAll
    static void stopEnvironment() {
        System.clearProperty("com.github.dropguard.summer.test.datasource.url");
        System.clearProperty("com.github.dropguard.summer.redis.uri");
        context = null;
    }
}
