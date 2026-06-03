package summer.tck.data.jdbc;

import static org.junit.jupiter.api.Assertions.*;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import summer.core.reflect.ClassInstantiator;
import summer.data.jdbc.JdbcTemplate;
import summer.data.jdbc.RowMapperRegistry;
import summer.tck.data.jdbc.dummy.User;

public abstract class AbstractJdbcTemplateTCK {

	private HikariDataSource dataSource;
	protected JdbcTemplate jdbcTemplate;

	@BeforeEach
	void setUp() {
		HikariConfig config = new HikariConfig();
		config.setJdbcUrl("jdbc:h2:mem:tck_test;DB_CLOSE_DELAY=-1");
		config.setUsername("sa");
		config.setPassword("");
		dataSource = new HikariDataSource(config);
		ClassInstantiator instantiator = className -> Class.forName(className).getDeclaredConstructor().newInstance();
		jdbcTemplate = new JdbcTemplate(dataSource, new RowMapperRegistry(instantiator));

		jdbcTemplate.update("CREATE TABLE IF NOT EXISTS users (id INT PRIMARY KEY, name VARCHAR(255))");
		jdbcTemplate.update("TRUNCATE TABLE users");
	}

	@AfterEach
	void tearDown() {
		if (dataSource != null) {
			dataSource.close();
		}
	}

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
	void testUpdate() {
		jdbcTemplate.update("INSERT INTO users (id, name) VALUES (?, ?)", 1, "Alice");

		int rows = jdbcTemplate.update("UPDATE users SET name = ? WHERE id = ?", "Alicia", 1);
		assertEquals(1, rows);

		User result = jdbcTemplate.queryForObject("SELECT id, name FROM users WHERE id = ?", User.class, 1);
		assertEquals("Alicia", result.name());
	}

	@Test
	void testDelete() {
		jdbcTemplate.update("INSERT INTO users (id, name) VALUES (?, ?)", 1, "Alice");

		int rows = jdbcTemplate.update("DELETE FROM users WHERE id = ?", 1);
		assertEquals(1, rows);

		List<User> results = jdbcTemplate.queryForList("SELECT id, name FROM users", User.class);
		assertTrue(results.isEmpty());
	}
}
