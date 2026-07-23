package summer.data.jdbc.query;

import static org.junit.jupiter.api.Assertions.*;
import static summer.data.jdbc.query.QueryTemplate.*;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.util.List;
import javax.sql.DataSource;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.Indexer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import summer.data.jdbc.EntityMetadataRegistry;
import summer.data.jdbc.JdbcTemplate;
import summer.data.jdbc.RowMapperFactory;
import summer.data.jdbc.annotation.RowModel;

/**
 * Integration tests for {@link QueryBuilder} using a real H2 database and a
 * real Jandex index — no demo application involved. Verifies SQL assembly,
 * column whitelist validation, and end-to-end query/map through
 * {@link JdbcTemplate}.
 */
class QueryBuilderIntegrationTest {

	@RowModel(table = "issues")
	record Issue(int id, String title, String status, String assignee) {
	}

	private DataSource dataSource;
	private JdbcTemplate jdbcTemplate;
	private QueryTemplate queryTemplate;

	@BeforeEach
	void setUp() throws Exception {
		HikariConfig config = new HikariConfig();
		config.setJdbcUrl("jdbc:h2:mem:qb_test;DB_CLOSE_DELAY=-1");
		config.setUsername("sa");
		config.setPassword("");
		dataSource = new HikariDataSource(config);

		jdbcTemplate = new JdbcTemplate(dataSource);
		jdbcTemplate.update("CREATE TABLE IF NOT EXISTS issues (id INT PRIMARY KEY, title VARCHAR(255), "
				+ "status VARCHAR(32), assignee VARCHAR(64))");

		// Register metadata + mapper from a real Jandex index over the Issue record,
		// mirroring what ReflectiveRowMapperRegistrar does during container assembly.
		Indexer indexer = new Indexer();
		try (var is = Issue.class.getResourceAsStream("/" + Issue.class.getName().replace('.', '/') + ".class")) {
			indexer.index(is);
		}
		IndexView index = indexer.complete();
		EntityMetadataRegistry registry = new EntityMetadataRegistry();
		for (var meta : RowMapperFactory.scanJandex(index)) {
			jdbcTemplate.registerMapper(Class.forName(meta.modelClassName()), RowMapperFactory.createReflective(meta));
			registry.register(meta);
		}
		queryTemplate = new QueryTemplate(jdbcTemplate, registry);

		jdbcTemplate.update("TRUNCATE TABLE issues");
		jdbcTemplate.update("INSERT INTO issues VALUES (1, 'First', 'OPEN', 'alice')");
		jdbcTemplate.update("INSERT INTO issues VALUES (2, 'Second', 'OPEN', 'bob')");
		jdbcTemplate.update("INSERT INTO issues VALUES (3, 'Third', 'CLOSED', 'alice')");
	}

	@AfterEach
	void tearDown() {
		if (dataSource != null) {
			((HikariDataSource) dataSource).close();
		}
	}

	@Test
	void selectsBySingleEquality() {
		List<Issue> open = queryTemplate.select(Issue.class).where(eq("status", "OPEN")).list();
		assertEquals(2, open.size());
		assertTrue(open.stream().allMatch(i -> i.status().equals("OPEN")));
	}

	@Test
	void selectsByCompositeAnd() {
		List<Issue> result = queryTemplate.select(Issue.class).where(eq("status", "OPEN"), eq("assignee", "alice"))
				.list();
		assertEquals(1, result.size());
		assertEquals("First", result.get(0).title());
	}

	@Test
	void selectsByOrGroup() {
		List<Issue> result = queryTemplate.select(Issue.class).where(or(eq("assignee", "alice"), eq("assignee", "bob")))
				.list();
		assertEquals(3, result.size());
	}

	@Test
	void comparisonAndOrdering() {
		List<Issue> result = queryTemplate.select(Issue.class).where(ge("id", 2)).orderBy("id").desc().list();
		assertEquals(List.of(3, 2), result.stream().map(Issue::id).toList());
	}

	@Test
	void limitAndOffset() {
		List<Issue> result = queryTemplate.select(Issue.class).orderBy("id").limit(2).offset(1).list();
		assertEquals(List.of(2, 3), result.stream().map(Issue::id).toList());
	}

	@Test
	void firstReturnsSingleOrNull() {
		Issue found = queryTemplate.select(Issue.class).where(eq("id", 3)).first();
		assertNotNull(found);
		assertEquals("Third", found.title());

		Issue missing = queryTemplate.select(Issue.class).where(eq("id", 999)).first();
		assertNull(missing);
	}

	@Test
	void countReturnsMatchCount() {
		long openCount = queryTemplate.select(Issue.class).where(eq("status", "OPEN")).count();
		assertEquals(2L, openCount);

		long total = queryTemplate.select(Issue.class).count();
		assertEquals(3L, total);
	}

	@Test
	void rejectsUnknownColumn() {
		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
				() -> queryTemplate.select(Issue.class).where(eq("not_a_column", "x")).list());
		assertTrue(ex.getMessage().contains("Unknown column"));
	}

	@Test
	void rejectsUnknownOrderByColumn() {
		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
				() -> queryTemplate.select(Issue.class).orderBy("bogus").list());
		assertTrue(ex.getMessage().contains("Unknown"));
	}

	@Test
	void insertPersistsAndIsQueryable() {
		int inserted = queryTemplate.insert(new Issue(10, "New", "OPEN", "carol"));
		assertEquals(1, inserted);

		Issue fetched = queryTemplate.select(Issue.class).where(eq("id", 10)).first();
		assertNotNull(fetched);
		assertEquals("New", fetched.title());
		assertEquals("carol", fetched.assignee());
	}

	@Test
	void saveIsInsertAlias() {
		queryTemplate.save(new Issue(11, "Saved", "CLOSED", "dave"));
		assertEquals(1, queryTemplate.select(Issue.class).where(eq("id", 11)).count());
	}

	@Test
	void updateChangesMatchedRows() {
		queryTemplate.update(new Issue(1, "Updated", "CLOSED", "alice")).where(eq("id", 1)).execute();

		Issue updated = queryTemplate.select(Issue.class).where(eq("id", 1)).first();
		assertEquals("Updated", updated.title());
		assertEquals("CLOSED", updated.status());
		// non-matched rows are unaffected
		assertEquals("OPEN", queryTemplate.select(Issue.class).where(eq("id", 2)).first().status());
	}

	@Test
	void deleteRemovesMatchedRows() {
		queryTemplate.delete(Issue.class).where(eq("status", "OPEN")).execute();

		assertEquals(1L, queryTemplate.select(Issue.class).count());
		assertEquals("Third", queryTemplate.select(Issue.class).first().title());
	}

	@Test
	void updateWithoutWhereIsRejected() {
		summer.core.exception.MissingWhereClauseException ex = assertThrows(
				summer.core.exception.MissingWhereClauseException.class,
				() -> queryTemplate.update(new Issue(1, "x", "OPEN", "alice")).execute());
		assertTrue(ex.getMessage().contains("WHERE"));
	}

	@Test
	void deleteWithoutWhereIsRejected() {
		summer.core.exception.MissingWhereClauseException ex = assertThrows(
				summer.core.exception.MissingWhereClauseException.class,
				() -> queryTemplate.delete(Issue.class).execute());
		assertTrue(ex.getMessage().contains("WHERE"));
	}

	@Test
	void partialUpdateSetsOnlyNamedColumns() {
		// Only status changes; other columns must keep their existing values.
		queryTemplate.update(Issue.class).set("status", "CLOSED").where(eq("id", 2)).execute();

		Issue updated = queryTemplate.select(Issue.class).where(eq("id", 2)).first();
		assertEquals("CLOSED", updated.status());
		assertEquals("Second", updated.title()); // untouched
		assertEquals("bob", updated.assignee()); // untouched
	}

	@Test
	void partialUpdateRejectsUnknownColumn() {
		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
				() -> queryTemplate.update(Issue.class).set("not_a_column", "x").where(eq("id", 1)).execute());
		assertTrue(ex.getMessage().contains("Unknown column"));
	}
}
