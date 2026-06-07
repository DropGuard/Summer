package summer.tck.data.jdbc;

import static org.junit.jupiter.api.Assertions.*;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import summer.data.jdbc.JdbcTemplate;
import summer.data.jdbc.RowMapperRegistry;
import summer.fixtures.data.jdbc.User;
import summer.tck.AbstractComponentTCK;

/**
 * TCK for JdbcTemplate CRUD operations.
 *
 * <p>
 * Subclasses provide a {@link RowMapperRegistry} via {@link #createRegistry()}
 * — Runtime runners register mappers manually, AOT runners use generated
 * {@code RowMapperConfiguration}.
 * </p>
 */
public abstract class AbstractJdbcTemplateTCK extends AbstractComponentTCK {

	private HikariDataSource dataSource;
	protected JdbcTemplate jdbcTemplate;

	/**
	 * Creates the RowMapperRegistry for this engine.
	 */
	protected abstract RowMapperRegistry createRegistry();
	@BeforeEach
	void setUpJdbcTemplate() {
		HikariConfig config = new HikariConfig();
		config.setJdbcUrl("jdbc:h2:mem:tck_test;DB_CLOSE_DELAY=-1");
		config.setUsername("sa");
		config.setPassword("");
		dataSource = new HikariDataSource(config);

		jdbcTemplate = new JdbcTemplate(dataSource, createRegistry());

		jdbcTemplate.update("CREATE TABLE IF NOT EXISTS users (id INT PRIMARY KEY, name VARCHAR(255))");
		jdbcTemplate.update("TRUNCATE TABLE users");
	}
	protected void cleanupComponent() {
		if (dataSource != null) {
			dataSource.close();
		}
	}

	// ---- INSERT + QUERY ----

	@Test
	void testInsertAndQueryForList() {
		jdbcTemplate.update("INSERT INTO users (id, name) VALUES (?, ?)", 1, "Alice");
		jdbcTemplate.update("INSERT INTO users (id, name) VALUES (?, ?)", 2, "Bob");

		List<User> results = jdbcTemplate.queryForList("SELECT id, name FROM users ORDER BY id", User.class);

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

		List<User> results = jdbcTemplate.queryForList("SELECT id, name FROM users WHERE name LIKE ? ORDER BY id",
				User.class, "Ali%");
		assertEquals(2, results.size());
		assertEquals("Alice", results.get(0).name());
		assertEquals("Alicia", results.get(1).name());
	}

	// ---- queryForObject ----

	@Test
	void testQueryForObject() {
		jdbcTemplate.update("INSERT INTO users (id, name) VALUES (?, ?)", 1, "Alice");

		User result = jdbcTemplate.queryForObject("SELECT id, name FROM users WHERE id = ?", User.class, 1);

		assertNotNull(result);
		assertEquals(1, result.id());
		assertEquals("Alice", result.name());
	}

	@Test
	void testQueryForObjectNotFound() {
		assertNull(jdbcTemplate.queryForObject("SELECT id, name FROM users WHERE id = ?", User.class, 999));
	}

	@Test
	void testQueryForObjectThrowsOnMultipleRows() {
		jdbcTemplate.update("INSERT INTO users (id, name) VALUES (?, ?)", 1, "Alice");
		jdbcTemplate.update("INSERT INTO users (id, name) VALUES (?, ?)", 2, "Bob");

		assertThrows(RuntimeException.class,
				() -> jdbcTemplate.queryForObject("SELECT id, name FROM users", User.class));
	}

	// ---- UPDATE ----

	@Test
	void testUpdate() {
		jdbcTemplate.update("INSERT INTO users (id, name) VALUES (?, ?)", 1, "Alice");

		int rows = jdbcTemplate.update("UPDATE users SET name = ? WHERE id = ?", "Alicia", 1);
		assertEquals(1, rows);

		User result = jdbcTemplate.queryForObject("SELECT id, name FROM users WHERE id = ?", User.class, 1);
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
		List<Object[]> batchArgs = List.of(new Object[]{1, "Alice"}, new Object[]{2, "Bob"},
				new Object[]{3, "Charlie"});

		int[] results = jdbcTemplate.batchUpdate("INSERT INTO users (id, name) VALUES (?, ?)", batchArgs);

		assertEquals(3, results.length);
		for (int r : results) {
			assertEquals(1, r);
		}

		List<User> users = jdbcTemplate.queryForList("SELECT id, name FROM users ORDER BY id", User.class);
		assertEquals(3, users.size());
		assertEquals("Alice", users.get(0).name());
		assertEquals("Bob", users.get(1).name());
		assertEquals("Charlie", users.get(2).name());
	}

	@Test
	void testBatchUpdateEmpty() {
		int[] results = jdbcTemplate.batchUpdate("INSERT INTO users (id, name) VALUES (?, ?)", List.of());
		assertEquals(0, results.length);
	}

	// ---- NULL handling ----

	@Test
	void testNullColumnValue() {
		jdbcTemplate.update("INSERT INTO users (id, name) VALUES (?, ?)", 1, null);

		User result = jdbcTemplate.queryForObject("SELECT id, name FROM users WHERE id = ?", User.class, 1);
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

		assertThrows(RuntimeException.class,
				() -> jdbcTemplate.update("INSERT INTO users (id, name) VALUES (?, ?)", 1, "Duplicate"));
	}

	@Test
	void testMissingRowMapperThrows() {
		record UnmappedModel(int x) {
		}

		assertThrows(RuntimeException.class,
				() -> jdbcTemplate.queryForList("SELECT id FROM users", UnmappedModel.class));
	}
}
