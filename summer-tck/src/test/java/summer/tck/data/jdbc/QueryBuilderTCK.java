package summer.tck.data.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import summer.core.BeanContainer;
import summer.data.jdbc.JdbcTemplate;
import summer.data.jdbc.query.QueryBuilder;
import summer.data.jdbc.query.QueryTemplate;
import summer.fixtures.data.jdbc.User;
import summer.test.annotation.DualEngine;
import summer.test.annotation.SummerTest;

/**
 * Dual-engine (Runtime + AOT) contract for
 * {@link QueryTemplate}/{@link QueryBuilder}.
 *
 * <p>
 * Uses the TCK's existing {@code User} / {@code users} fixture. Verifies that
 * fluent queries assemble correct SQL and share result mapping with the rest of
 * the data module on both engines — the framework-enforced parity guarantee.
 * </p>
 */
@SummerTest
public class QueryBuilderTCK {

	private final BeanContainer context;
	private QueryTemplate queryTemplate;

	public QueryBuilderTCK(BeanContainer context) {
		this.context = context;
	}

	@BeforeEach
	void setUp() {
		queryTemplate = context.getBean(QueryTemplate.class);
		JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
		jdbcTemplate.update("CREATE TABLE IF NOT EXISTS users (id INT PRIMARY KEY, name VARCHAR(255))");
		jdbcTemplate.update("TRUNCATE TABLE users");
		jdbcTemplate.update("INSERT INTO users VALUES (1, 'Alice')");
		jdbcTemplate.update("INSERT INTO users VALUES (2, 'Bob')");
		jdbcTemplate.update("INSERT INTO users VALUES (3, 'Alice')");
	}

	@DualEngine
	void selectByEquality() {
		var alices = queryTemplate.select(User.class).where(QueryTemplate.eq("name", "Alice")).list();
		assertEquals(2, alices.size());
		assertTrue(alices.stream().allMatch(u -> u.name().equals("Alice")));
	}

	@DualEngine
	void countAndFirst() {
		assertEquals(3L, queryTemplate.select(User.class).count());

		User bob = queryTemplate.select(User.class).where(QueryTemplate.eq("name", "Bob")).first();
		assertEquals("Bob", bob.name());
	}

	@DualEngine
	void orderingAndLimit() {
		var topTwo = queryTemplate.select(User.class).orderBy("id").limit(2).list();
		assertEquals(2, topTwo.size());
		assertEquals(1, topTwo.get(0).id());
		assertEquals(2, topTwo.get(1).id());
	}

	@DualEngine
	void insertPersists() {
		assertEquals(1, queryTemplate.insert(new User(4, "Carol")));

		User carol = queryTemplate.select(User.class).where(QueryTemplate.eq("id", 4)).first();
		assertEquals("Carol", carol.name());
	}

	@DualEngine
	void updateById() {
		queryTemplate.update(new User(2, "Bobby")).where(QueryTemplate.eq("id", 2)).execute();

		User updated = queryTemplate.select(User.class).where(QueryTemplate.eq("id", 2)).first();
		assertEquals("Bobby", updated.name());
	}

	@DualEngine
	void partialUpdateSetsOnlyNamedColumn() {
		queryTemplate.update(User.class).set("name", "Bobby").where(QueryTemplate.eq("id", 2)).execute();

		User updated = queryTemplate.select(User.class).where(QueryTemplate.eq("id", 2)).first();
		assertEquals("Bobby", updated.name());
	}

	@DualEngine
	void deleteById() {
		queryTemplate.delete(User.class).where(QueryTemplate.eq("id", 1)).execute();

		assertEquals(2L, queryTemplate.select(User.class).count());
		assertEquals("Bob", queryTemplate.select(User.class).where(QueryTemplate.eq("name", "Bob")).first().name());
	}
}
