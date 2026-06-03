package summer.data.jdbc;

import static org.junit.jupiter.api.Assertions.*;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class JdbcTemplateTest {

	private HikariDataSource dataSource;
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	public void setUp() {
		HikariConfig config = new HikariConfig();
		config.setJdbcUrl("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1");
		config.setUsername("sa");
		config.setPassword("");
		dataSource = new HikariDataSource(config);
		RowMapperRegistry rowMapperRegistry = new RowMapperRegistry(className -> Class.forName(className).getDeclaredConstructor().newInstance());
		jdbcTemplate = new JdbcTemplate(dataSource, rowMapperRegistry);

		jdbcTemplate.update("CREATE TABLE IF NOT EXISTS test_users (id INT PRIMARY KEY, name VARCHAR(255))");
		jdbcTemplate.update("TRUNCATE TABLE test_users");
	}

	@AfterEach
	public void tearDown() {
		if (dataSource != null) {
			dataSource.close();
		}
	}

	// ---- INSERT ----

	@Test
	public void testInsertAndQueryForList() {
		int rows = jdbcTemplate.update("INSERT INTO test_users (id, name) VALUES (?, ?)", 1, "Alice");
		assertEquals(1, rows);

		jdbcTemplate.update("INSERT INTO test_users (id, name) VALUES (?, ?)", 2, "Bob");

		List<TestModel> results = jdbcTemplate.queryForList("SELECT id, name FROM test_users ORDER BY id",
				TestModel.class);

		assertEquals(2, results.size());
		assertEquals(1, results.get(0).id());
		assertEquals("Alice", results.get(0).name());
		assertEquals(2, results.get(1).id());
		assertEquals("Bob", results.get(1).name());
	}

	// ---- UPDATE ----

	@Test
	public void testUpdate() {
		jdbcTemplate.update("INSERT INTO test_users (id, name) VALUES (?, ?)", 1, "Alice");

		int rows = jdbcTemplate.update("UPDATE test_users SET name = ? WHERE id = ?", "Alicia", 1);
		assertEquals(1, rows);

		TestModel result = jdbcTemplate.queryForObject("SELECT id, name FROM test_users WHERE id = ?", TestModel.class,
				1);
		assertEquals("Alicia", result.name());
	}

	@Test
	public void testUpdateNoMatchingRows() {
		int rows = jdbcTemplate.update("UPDATE test_users SET name = ? WHERE id = ?", "Ghost", 999);
		assertEquals(0, rows);
	}

	// ---- DELETE ----

	@Test
	public void testDelete() {
		jdbcTemplate.update("INSERT INTO test_users (id, name) VALUES (?, ?)", 1, "Alice");
		jdbcTemplate.update("INSERT INTO test_users (id, name) VALUES (?, ?)", 2, "Bob");

		int rows = jdbcTemplate.update("DELETE FROM test_users WHERE id = ?", 1);
		assertEquals(1, rows);

		List<TestModel> results = jdbcTemplate.queryForList("SELECT id, name FROM test_users", TestModel.class);
		assertEquals(1, results.size());
		assertEquals("Bob", results.get(0).name());
	}

	@Test
	public void testDeleteNoMatchingRows() {
		int rows = jdbcTemplate.update("DELETE FROM test_users WHERE id = ?", 999);
		assertEquals(0, rows);
	}

	// ---- batchUpdate ----

	@Test
	public void testBatchUpdate() {
		List<Object[]> batchArgs = List.of(new Object[]{1, "Alice"}, new Object[]{2, "Bob"},
				new Object[]{3, "Charlie"});

		int[] results = jdbcTemplate.batchUpdate("INSERT INTO test_users (id, name) VALUES (?, ?)", batchArgs);

		assertEquals(3, results.length);
		for (int r : results) {
			assertEquals(1, r);
		}

		List<TestModel> users = jdbcTemplate.queryForList("SELECT id, name FROM test_users ORDER BY id",
				TestModel.class);
		assertEquals(3, users.size());
		assertEquals("Alice", users.get(0).name());
		assertEquals("Bob", users.get(1).name());
		assertEquals("Charlie", users.get(2).name());
	}

	@Test
	public void testBatchUpdateEmpty() {
		int[] results = jdbcTemplate.batchUpdate("INSERT INTO test_users (id, name) VALUES (?, ?)", List.of());
		assertEquals(0, results.length);
	}

	// ---- queryForObject ----

	@Test
	public void testQueryForObject() {
		jdbcTemplate.update("INSERT INTO test_users (id, name) VALUES (?, ?)", 1, "Alice");

		TestModel result = jdbcTemplate.queryForObject("SELECT id, name FROM test_users WHERE id = ?", TestModel.class,
				1);

		assertNotNull(result);
		assertEquals(1, result.id());
		assertEquals("Alice", result.name());
	}

	@Test
	public void testQueryForObjectReturnsNull() {
		TestModel result = jdbcTemplate.queryForObject("SELECT id, name FROM test_users WHERE id = ?", TestModel.class,
				999);
		assertNull(result);
	}

	@Test
	public void testQueryForObjectThrowsOnMultipleRows() {
		jdbcTemplate.update("INSERT INTO test_users (id, name) VALUES (?, ?)", 1, "Alice");
		jdbcTemplate.update("INSERT INTO test_users (id, name) VALUES (?, ?)", 2, "Bob");

		assertThrows(RuntimeException.class,
				() -> jdbcTemplate.queryForObject("SELECT id, name FROM test_users", TestModel.class));
	}

	// ---- queryForList ----

	@Test
	public void testQueryForListEmpty() {
		List<TestModel> results = jdbcTemplate.queryForList("SELECT id, name FROM test_users", TestModel.class);
		assertTrue(results.isEmpty());
	}

	@Test
	public void testQueryForListWithWhereClause() {
		jdbcTemplate.update("INSERT INTO test_users (id, name) VALUES (?, ?)", 1, "Alice");
		jdbcTemplate.update("INSERT INTO test_users (id, name) VALUES (?, ?)", 2, "Bob");
		jdbcTemplate.update("INSERT INTO test_users (id, name) VALUES (?, ?)", 3, "Alicia");

		List<TestModel> results = jdbcTemplate
				.queryForList("SELECT id, name FROM test_users WHERE name LIKE ? ORDER BY id", TestModel.class, "Ali%");
		assertEquals(2, results.size());
		assertEquals("Alice", results.get(0).name());
		assertEquals("Alicia", results.get(1).name());
	}

	// ---- NULL handling ----

	@Test
	public void testNullColumnValue() {
		jdbcTemplate.update("INSERT INTO test_users (id, name) VALUES (?, ?)", 1, null);

		TestModel result = jdbcTemplate.queryForObject("SELECT id, name FROM test_users WHERE id = ?", TestModel.class,
				1);
		assertNotNull(result);
		assertEquals(1, result.id());
		assertNull(result.name());
	}

	// ---- Error cases ----

	@Test
	public void testInvalidSqlThrows() {
		assertThrows(RuntimeException.class, () -> jdbcTemplate.update("NOT VALID SQL"));
	}

	@Test
	public void testDuplicateKeyThrows() {
		jdbcTemplate.update("INSERT INTO test_users (id, name) VALUES (?, ?)", 1, "Alice");

		assertThrows(RuntimeException.class,
				() -> jdbcTemplate.update("INSERT INTO test_users (id, name) VALUES (?, ?)", 1, "Duplicate"));
	}

	// ---- RowMapperRegistry ----

	@Test
	public void testMissingRowMapperThrows() {
		record UnmappedModel(int x) {
		}

		assertThrows(RuntimeException.class,
				() -> jdbcTemplate.queryForList("SELECT id FROM test_users", UnmappedModel.class));
	}
}
