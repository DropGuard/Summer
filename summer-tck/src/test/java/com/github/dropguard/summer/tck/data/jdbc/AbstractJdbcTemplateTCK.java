package com.github.dropguard.summer.tck.data.jdbc;

import static org.junit.jupiter.api.Assertions.*;

import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.data.jdbc.JdbcTemplate;
import com.github.dropguard.summer.fixtures.data.jdbc.User;
import com.github.dropguard.summer.tck.AbstractTCK;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * TCK for JdbcTemplate CRUD operations.
 *
 * <p>The {@link JdbcTemplate} is obtained from the DI container (via {@link #context}), so both
 * engines (Runtime + AOT) wire it through their own assembly path. {@code
 * ReflectiveRowMapperRegistrar} (Runtime engine) auto-registers every {@code @RowModel} (e.g.
 * {@link User}) — the real engine-specific behaviour this TCK must verify. The container is
 * supplied by the {@code @SummerTest} subclass constructor; a concrete subclass exposes the
 * {@code @Test} methods as {@code @DualEngine} so the framework runs them on both engines, proving
 * parity.
 */
public abstract class AbstractJdbcTemplateTCK extends AbstractTCK {

    // Per-invocation container: refreshed by the @BeforeEach below for EVERY
    // @DualEngine invocation (resolved via the invocation's ParameterResolver).
    protected BeanContainer context;
    protected JdbcTemplate jdbcTemplate;
    protected DataSource dataSource;

    @BeforeEach
    void setUpJdbcTemplate(BeanContainer context) {
        this.context = context;
        jdbcTemplate = context.getBean(JdbcTemplate.class);
        dataSource = context.getBean(DataSource.class);

        jdbcTemplate.update(
                "CREATE TABLE IF NOT EXISTS users (id INT PRIMARY KEY, name VARCHAR(255))");
        jdbcTemplate.update("TRUNCATE TABLE users");
    }

    // ---- INSERT + QUERY ----

    @Test
    void testInsertAndQueryForList() {
        jdbcTemplate.update("INSERT INTO users (id, name) VALUES (?, ?)", 1, "Alice");
        jdbcTemplate.update("INSERT INTO users (id, name) VALUES (?, ?)", 2, "Bob");

        List<User> results =
                jdbcTemplate.queryForList("SELECT id, name FROM users ORDER BY id", User.class);

        assertEquals(2, results.size());
        assertEquals(1, results.get(0).id());
        assertEquals("Alice", results.get(0).name());
        assertEquals(2, results.get(1).id());
        assertEquals("Bob", results.get(1).name());
    }

    @Test
    void testQueryForListEmpty() {
        List<User> results = jdbcTemplate.queryForList("SELECT id, name FROM users", User.class);
        assertTrue(results.isEmpty());
    }

    @Test
    void testQueryForListWithWhereClause() {
        jdbcTemplate.update("INSERT INTO users (id, name) VALUES (?, ?)", 1, "Alice");
        jdbcTemplate.update("INSERT INTO users (id, name) VALUES (?, ?)", 2, "Bob");
        jdbcTemplate.update("INSERT INTO users (id, name) VALUES (?, ?)", 3, "Alicia");

        List<User> results =
                jdbcTemplate.queryForList(
                        "SELECT id, name FROM users WHERE name LIKE ? ORDER BY id",
                        User.class,
                        "Ali%");
        assertEquals(2, results.size());
        assertEquals("Alice", results.get(0).name());
        assertEquals("Alicia", results.get(1).name());
    }

    // ---- queryForObject ----

    @Test
    void testQueryForObject() {
        jdbcTemplate.update("INSERT INTO users (id, name) VALUES (?, ?)", 1, "Alice");

        User result =
                jdbcTemplate.queryForObject(
                        "SELECT id, name FROM users WHERE id = ?", User.class, 1);

        assertNotNull(result);
        assertEquals(1, result.id());
        assertEquals("Alice", result.name());
    }

    @Test
    void testQueryForObjectNotFound() {
        assertNull(
                jdbcTemplate.queryForObject(
                        "SELECT id, name FROM users WHERE id = ?", User.class, 999));
    }

    @Test
    void testQueryForObjectThrowsOnMultipleRows() {
        jdbcTemplate.update("INSERT INTO users (id, name) VALUES (?, ?)", 1, "Alice");
        jdbcTemplate.update("INSERT INTO users (id, name) VALUES (?, ?)", 2, "Bob");

        assertThrows(
                RuntimeException.class,
                () -> jdbcTemplate.queryForObject("SELECT id, name FROM users", User.class));
    }

    // ---- UPDATE ----

    @Test
    void testUpdate() {
        jdbcTemplate.update("INSERT INTO users (id, name) VALUES (?, ?)", 1, "Alice");

        int rows = jdbcTemplate.update("UPDATE users SET name = ? WHERE id = ?", "Alicia", 1);
        assertEquals(1, rows);

        User result =
                jdbcTemplate.queryForObject(
                        "SELECT id, name FROM users WHERE id = ?", User.class, 1);
        assertEquals("Alicia", result.name());
    }

    @Test
    void testUpdateNoMatchingRows() {
        int rows = jdbcTemplate.update("UPDATE users SET name = ? WHERE id = ?", "Ghost", 999);
        assertEquals(0, rows);
    }

    // ---- DELETE ----

    @Test
    void testDelete() {
        jdbcTemplate.update("INSERT INTO users (id, name) VALUES (?, ?)", 1, "Alice");
        jdbcTemplate.update("INSERT INTO users (id, name) VALUES (?, ?)", 2, "Bob");

        int rows = jdbcTemplate.update("DELETE FROM users WHERE id = ?", 1);
        assertEquals(1, rows);

        List<User> results = jdbcTemplate.queryForList("SELECT id, name FROM users", User.class);
        assertEquals(1, results.size());
        assertEquals("Bob", results.get(0).name());
    }

    @Test
    void testDeleteNoMatchingRows() {
        int rows = jdbcTemplate.update("DELETE FROM users WHERE id = ?", 999);
        assertEquals(0, rows);
    }

    // ---- batchUpdate ----

    @Test
    void testBatchUpdate() {
        List<Object[]> batchArgs =
                List.of(
                        new Object[] {1, "Alice"},
                        new Object[] {2, "Bob"},
                        new Object[] {3, "Charlie"});

        int[] results =
                jdbcTemplate.batchUpdate("INSERT INTO users (id, name) VALUES (?, ?)", batchArgs);

        assertEquals(3, results.length);
        for (int r : results) {
            assertEquals(1, r);
        }

        List<User> users =
                jdbcTemplate.queryForList("SELECT id, name FROM users ORDER BY id", User.class);
        assertEquals(3, users.size());
        assertEquals("Alice", users.get(0).name());
        assertEquals("Bob", users.get(1).name());
        assertEquals("Charlie", users.get(2).name());
    }

    @Test
    void testBatchUpdateEmpty() {
        int[] results =
                jdbcTemplate.batchUpdate("INSERT INTO users (id, name) VALUES (?, ?)", List.of());
        assertEquals(0, results.length);
    }

    // ---- NULL handling ----

    @Test
    void testNullColumnValue() {
        jdbcTemplate.update("INSERT INTO users (id, name) VALUES (?, ?)", 1, null);

        User result =
                jdbcTemplate.queryForObject(
                        "SELECT id, name FROM users WHERE id = ?", User.class, 1);
        assertNotNull(result);
        assertEquals(1, result.id());
        assertNull(result.name());
    }

    // ---- Error cases ----

    @Test
    void testInvalidSqlThrows() {
        assertThrows(RuntimeException.class, () -> jdbcTemplate.update("NOT VALID SQL"));
    }

    @Test
    void testDuplicateKeyThrows() {
        jdbcTemplate.update("INSERT INTO users (id, name) VALUES (?, ?)", 1, "Alice");

        assertThrows(
                RuntimeException.class,
                () ->
                        jdbcTemplate.update(
                                "INSERT INTO users (id, name) VALUES (?, ?)", 1, "Duplicate"));
    }

    @Test
    void testMissingRowMapperThrows() {
        record UnmappedModel(int x) {}

        assertThrows(
                RuntimeException.class,
                () -> jdbcTemplate.queryForList("SELECT id FROM users", UnmappedModel.class));
    }
}
