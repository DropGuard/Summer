package com.github.dropguard.summer.data.jdbc.it;

import com.github.dropguard.summer.core.BeanContainer;
import java.sql.Connection;
import java.sql.Statement;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared Postgres bootstrap for the data-jdbc real-database integration tests.
 *
 * <p>Postgres starts once per test class. System properties are set in {@code @BeforeAll} before
 * any {@code @SummerTest} container builds. Tables are dropped and re-created in
 * {@code @BeforeEach} so every {@code @DualEngine} invocation sees a clean schema.
 */
@Testcontainers
abstract class AbstractFrameworkIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:15"))
                    .withDatabaseName("summer_it")
                    .withUsername("test")
                    .withPassword("test");

    protected final BeanContainer context;

    protected AbstractFrameworkIT(BeanContainer context) {
        this.context = context;
    }

    @BeforeAll
    static void startEnvironment() throws Exception {
        System.setProperty(
                "com.github.dropguard.summer.test.datasource.url", POSTGRES.getJdbcUrl());
        System.setProperty("com.github.dropguard.summer.test.datasource.username", "test");
        System.setProperty("com.github.dropguard.summer.test.datasource.password", "test");
    }

    @BeforeEach
    void setupSchema() throws Exception {
        try (Connection conn = POSTGRES.createConnection("");
                Statement st = conn.createStatement()) {
            st.execute("DROP TABLE IF EXISTS persons");
            st.execute(
                    "CREATE TABLE persons (id BIGINT PRIMARY KEY, name VARCHAR(255), age INT,"
                            + " status VARCHAR(64))");
        }
    }

    @AfterAll
    static void stopEnvironment() {
        System.clearProperty("com.github.dropguard.summer.test.datasource.url");
        System.clearProperty("com.github.dropguard.summer.test.datasource.username");
        System.clearProperty("com.github.dropguard.summer.test.datasource.password");
    }
}
