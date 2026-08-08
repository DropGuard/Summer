package com.github.dropguard.summer.aot.db;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.github.dropguard.summer.data.jdbc.JdbcTemplate;
import com.github.dropguard.summer.test.SummerTestExtension;
import com.github.dropguard.summer.test.annotation.DualEngine;
import com.github.dropguard.summer.test.annotation.SummerTest;
import com.github.dropguard.summer.test.annotation.TestResource;
import com.github.dropguard.summer.test.db.PostgresTestResource;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * The production shape — AOT + a real Postgres — which the H2-backed dual-engine TCK tests and the
 * runtime-only real-DB module ITs each leave uncovered on their own: this runs the real database on
 * BOTH engines, binding the datasource from the shared {@link PostgresTestResource}'s overrides.
 * Lives in the aot-engine's tests — the only module whose classpath has BOTH the aot-engine and the
 * data-jdbc without a dependency cycle, and whose test-classes the tck's whole-universe does not
 * scan (per-module test isolation).
 */
@SummerTest
@TestResource(PostgresTestResource.class)
public class RealPostgresAotIntegrationIT {

    @RegisterExtension
    static SummerTestExtension ext =
            SummerTestExtension.builder()
                    .beanClasses(
                            AotRealPostgresConfig.class,
                            AotRealPostgresConfig.DataSourceProps.class)
                    .build();

    private final JdbcTemplate jdbcTemplate;

    public RealPostgresAotIntegrationIT(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @DualEngine
    void realPostgresQueryOnBothEngines() {
        jdbcTemplate.update("DROP TABLE IF EXISTS rp_users");
        jdbcTemplate.update("CREATE TABLE rp_users (id BIGINT PRIMARY KEY, name VARCHAR(100))");
        jdbcTemplate.update("INSERT INTO rp_users (id, name) VALUES (1, 'Alice')");

        Integer count =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM rp_users WHERE name = 'Alice'", Integer.class);
        assertEquals(1, count);
    }

    @DualEngine
    void criteriaQueryAndPartialUpdate() {
        jdbcTemplate.update("DROP TABLE IF EXISTS rp_users");
        jdbcTemplate.update(
                "CREATE TABLE rp_users (id BIGINT PRIMARY KEY, name VARCHAR(100), status"
                        + " VARCHAR(64))");
        jdbcTemplate.update(
                "INSERT INTO rp_users (id, name, status) VALUES (1, 'Grace', 'active')");
        jdbcTemplate.update(
                "INSERT INTO rp_users (id, name, status) VALUES (2, 'Katherine', 'active')");
        jdbcTemplate.update(
                "INSERT INTO rp_users (id, name, status) VALUES (3, 'Margaret', 'retired')");

        // The criteria/partial-update behaviors the module's real-DB ITs exercise on the runtime
        // engine only — here the same real Postgres on BOTH engines (the production shape).
        java.util.List<String> activeNames =
                jdbcTemplate.queryForList(
                        "SELECT name FROM rp_users WHERE status = ? ORDER BY id",
                        String.class,
                        "active");
        assertEquals(java.util.List.of("Grace", "Katherine"), activeNames);

        // Partial update: only the targeted column changes.
        jdbcTemplate.update("UPDATE rp_users SET status = ? WHERE id = ?", "retired", 1);
        String status =
                jdbcTemplate.queryForObject(
                        "SELECT status FROM rp_users WHERE id = 1", String.class);
        assertEquals("retired", status);
    }
}
