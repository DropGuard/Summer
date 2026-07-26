package com.github.dropguard.summer.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.github.dropguard.summer.data.jdbc.JdbcTemplate;
import com.github.dropguard.summer.test.annotation.DualEngine;
import com.github.dropguard.summer.test.annotation.SummerTest;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;

/**
 * Framework integration contract for {@link QueryTemplate} on a REAL Postgres.
 *
 * <p>
 * This is the layer H2 cannot validate faithfully: a real JDBC driver's type
 * mapping (here {@code TIMESTAMP} → {@code LocalDateTime}), dialect-correct
 * SQL, and transaction connection sharing under a live database. It runs
 * dual-engine (Runtime + AOT) against the shared dev-services Postgres, exactly
 * like {@link GreetingIT}, so QueryBuilder parity is asserted by the framework.
 * </p>
 */
@SummerTest
public class IssueQueryIT extends AbstractFrameworkIT {

	@BeforeEach
	void setUpSchema() {
		JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
		jdbc.update("CREATE TABLE IF NOT EXISTS it_issues ("
				+ "id BIGINT PRIMARY KEY, title VARCHAR(255), status VARCHAR(32), "
				+ "assignee VARCHAR(64), created_at TIMESTAMP)");
		// Shared Postgres across both engines: clear before each test.
		jdbc.update("DELETE FROM it_issues");
	}

	@DualEngine
	void saveAndFindByIdRoundTrip() {
		ItIssueQueryRepo repo = context.getBean(ItIssueQueryRepo.class);
		ItIssue saved = new ItIssue(1L, "Write real PG test", "OPEN", "alice", LocalDateTime.of(2024, 5, 1, 9, 30));
		repo.save(saved);

		ItIssue found = repo.findById(1L);
		assertNotNull(found);
		assertEquals("Write real PG test", found.title());
		assertEquals("OPEN", found.status());
		// Real driver type mapping: TIMESTAMP → LocalDateTime, not a string.
		assertEquals(LocalDateTime.of(2024, 5, 1, 9, 30), found.createdAt());
	}

	@DualEngine
	void selectByCriteriaAndCount() {
		ItIssueQueryRepo repo = context.getBean(ItIssueQueryRepo.class);
		repo.save(new ItIssue(1L, "a", "OPEN", "alice", LocalDateTime.now()));
		repo.save(new ItIssue(2L, "b", "CLOSED", "bob", LocalDateTime.now()));
		repo.save(new ItIssue(3L, "c", "OPEN", "carol", LocalDateTime.now()));

		assertEquals(2, repo.findByStatus("OPEN").size());
		assertEquals(3, repo.count());
	}

	@DualEngine
	void partialUpdateChangesOnlyStatus() {
		ItIssueQueryRepo repo = context.getBean(ItIssueQueryRepo.class);
		repo.save(new ItIssue(1L, "keep title", "OPEN", "alice", LocalDateTime.now()));

		repo.setStatus(1L, "CLOSED");

		ItIssue updated = repo.findById(1L);
		assertEquals("CLOSED", updated.status());
		assertEquals("keep title", updated.title()); // untouched column preserved on real PG
	}

	@DualEngine
	void deleteOnRealPg() {
		ItIssueQueryRepo repo = context.getBean(ItIssueQueryRepo.class);
		repo.save(new ItIssue(1L, "a", "OPEN", "alice", LocalDateTime.now()));
		repo.save(new ItIssue(2L, "b", "OPEN", "bob", LocalDateTime.now()));
		assertEquals(2, repo.count());

		repo.delete(1L);

		assertEquals(1, repo.count());
		assertEquals("b", repo.findById(2L).title());
	}
}
