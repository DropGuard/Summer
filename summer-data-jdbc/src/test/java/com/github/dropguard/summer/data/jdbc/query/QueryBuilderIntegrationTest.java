package com.github.dropguard.summer.data.jdbc.query;

import static com.github.dropguard.summer.data.jdbc.query.QueryTemplate.*;
import static org.junit.jupiter.api.Assertions.*;

import com.github.dropguard.summer.core.data.PageRequest;
import com.github.dropguard.summer.data.jdbc.EntityMetadataRegistry;
import com.github.dropguard.summer.data.jdbc.JdbcTemplate;
import com.github.dropguard.summer.data.jdbc.RowMapperFactory;
import com.github.dropguard.summer.data.jdbc.annotation.RowModel;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.sql.DataSource;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.Indexer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for {@link QueryBuilder} using a real H2 database and a real Jandex index — no
 * demo application involved. Verifies SQL assembly, column whitelist validation, and end-to-end
 * query/map through {@link JdbcTemplate}.
 */
class QueryBuilderIntegrationTest {

    @RowModel(table = "issues")
    record Issue(Integer id, String title, String status, String assignee) {}

    @RowModel(table = "issue_tags")
    record IssueTag(Integer issueId, Integer tagId) {}

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
        jdbcTemplate.update(
                "CREATE TABLE IF NOT EXISTS issues (id INT PRIMARY KEY, title VARCHAR(255), "
                        + "status VARCHAR(32), assignee VARCHAR(64))");

        // Register metadata + mapper from a real Jandex index over the Issue record,
        // mirroring what ReflectiveRowMapperRegistrar does during container assembly.
        Indexer indexer = new Indexer();
        for (Class<?> model : List.of(Issue.class, IssueTag.class)) {
            try (var is =
                    model.getResourceAsStream("/" + model.getName().replace('.', '/') + ".class")) {
                indexer.index(is);
            }
        }
        IndexView index = indexer.complete();
        EntityMetadataRegistry registry = new EntityMetadataRegistry();
        for (var meta : RowMapperFactory.scanJandex(index)) {
            jdbcTemplate.registerMapper(
                    Class.forName(meta.modelClassName()), RowMapperFactory.createReflective(meta));
            registry.register(meta);
        }
        queryTemplate = new QueryTemplate(jdbcTemplate, registry);

        jdbcTemplate.update("TRUNCATE TABLE issues");
        jdbcTemplate.update("INSERT INTO issues VALUES (1, 'First', 'OPEN', 'alice')");
        jdbcTemplate.update("INSERT INTO issues VALUES (2, 'Second', 'OPEN', 'bob')");
        jdbcTemplate.update("INSERT INTO issues VALUES (3, 'Third', 'CLOSED', 'alice')");
        jdbcTemplate.update("CREATE TABLE IF NOT EXISTS issue_tags (issue_id INT, tag_id INT)");
        jdbcTemplate.update("INSERT INTO issue_tags VALUES (1, 10), (1, 20), (2, 10)");
    }

    @AfterEach
    void tearDown() {
        if (dataSource != null) {
            jdbcTemplate.update("TRUNCATE TABLE issue_tags");
            ((HikariDataSource) dataSource).close();
        }
    }

    @Test
    void selectsBySingleEquality() {
        // count() asserts the number of matches; list().limit(N) asserts row content. The limit is
        // explicit (the framework never assumes a page size) and count() confirms no row was
        // truncated.
        assertEquals(2L, queryTemplate.select(Issue.class).where(eq("status", "OPEN")).count());
        List<Issue> open =
                queryTemplate.select(Issue.class).where(eq("status", "OPEN")).limit(100).list();
        assertEquals(2, open.size());
        assertTrue(open.stream().allMatch(i -> i.status().equals("OPEN")));
    }

    @Test
    void selectsByCompositeAnd() {
        assertEquals(
                1L,
                queryTemplate
                        .select(Issue.class)
                        .where(eq("status", "OPEN"), eq("assignee", "alice"))
                        .count());
        List<Issue> result =
                queryTemplate
                        .select(Issue.class)
                        .where(eq("status", "OPEN"), eq("assignee", "alice"))
                        .limit(100)
                        .list();
        assertEquals(1, result.size());
        assertEquals("First", result.get(0).title());
    }

    @Test
    void selectsByOrGroup() {
        assertEquals(
                3L,
                queryTemplate
                        .select(Issue.class)
                        .where(or(eq("assignee", "alice"), eq("assignee", "bob")))
                        .count());
        List<Issue> result =
                queryTemplate
                        .select(Issue.class)
                        .where(or(eq("assignee", "alice"), eq("assignee", "bob")))
                        .limit(100)
                        .list();
        assertEquals(3, result.size());
    }

    @Test
    void comparisonAndOrdering() {
        assertEquals(2L, queryTemplate.select(Issue.class).where(ge("id", 2)).count());
        List<Issue> result =
                queryTemplate
                        .select(Issue.class)
                        .where(ge("id", 2))
                        .orderBy("id")
                        .desc()
                        .limit(100)
                        .list();
        assertEquals(List.of(3, 2), result.stream().map(Issue::id).toList());
    }

    @Test
    void limitAndOffset() {
        List<Issue> result =
                queryTemplate.select(Issue.class).orderBy("id").limit(2).offset(1).list();
        assertEquals(List.of(2, 3), result.stream().map(Issue::id).toList());
    }

    @Test
    void pageReturnsWindowAndTotal() {
        // 3 rows total; page (0, 2) fetches ids 1,2 and reports total=3.
        var page0 = queryTemplate.select(Issue.class).orderBy("id").page(new PageRequest(0, 2));
        assertEquals(List.of(1, 2), page0.content().stream().map(Issue::id).toList());
        assertEquals(3L, page0.total());
        assertEquals(0, page0.page());
        assertEquals(2, page0.size());
    }

    @Test
    void pageLastPageReturnsRemainder() {
        // page (1, 2) fetches the third row; total stays 3.
        var page1 = queryTemplate.select(Issue.class).orderBy("id").page(new PageRequest(1, 2));
        assertEquals(List.of(3), page1.content().stream().map(Issue::id).toList());
        assertEquals(3L, page1.total());
        assertEquals(1, page1.page());
        assertEquals(2, page1.size());
    }

    @Test
    void pageTotalIsNotInflatedByExistsFilter() {
        // Tag filter (tag_id=10) matches issues 1 and 2 via EXISTS; total must be 2, not the
        // multiplied row count.
        var page =
                queryTemplate
                        .select(Issue.class)
                        .exists(
                                IssueTag.class,
                                "it",
                                and(eqCol("it.issue_id", "root.id"), eq("it.tag_id", 10)))
                        .orderBy("id")
                        .page(new PageRequest(0, 10));
        assertEquals(List.of(1, 2), page.content().stream().map(Issue::id).toList());
        assertEquals(2L, page.total());
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
        IllegalArgumentException ex =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                queryTemplate
                                        .select(Issue.class)
                                        .where(eq("not_a_column", "x"))
                                        .list());
        assertTrue(ex.getMessage().contains("Unknown column"));
    }

    @Test
    void inMatchesRowsWhoseColumnIsInTheSet() {
        // in() is the batch-loading primitive: one query for a set of keys.
        List<Issue> result =
                queryTemplate
                        .select(Issue.class)
                        .where(in("id", List.of(1, 3)))
                        .orderBy("id")
                        .limit(100)
                        .list();
        assertEquals(List.of(1, 3), result.stream().map(Issue::id).toList());
    }

    @Test
    void inWithEmptySetMatchesNothing() {
        // An empty IN set must not render invalid "IN ()" SQL nor match everything.
        long count = queryTemplate.select(Issue.class).where(in("id", List.of())).count();
        assertEquals(0L, count);
    }

    @Test
    void rejectsUnknownOrderByColumn() {
        IllegalArgumentException ex =
                assertThrows(
                        IllegalArgumentException.class,
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
        queryTemplate
                .update(new Issue(1, "Updated", "CLOSED", "alice"))
                .where(eq("id", 1))
                .execute();

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
        com.github.dropguard.summer.core.exception.MissingWhereClauseException ex =
                assertThrows(
                        com.github.dropguard.summer.core.exception.MissingWhereClauseException
                                .class,
                        () -> queryTemplate.update(new Issue(1, "x", "OPEN", "alice")).execute());
        assertTrue(ex.getMessage().contains("WHERE"));
    }

    @Test
    void deleteWithoutWhereIsRejected() {
        com.github.dropguard.summer.core.exception.MissingWhereClauseException ex =
                assertThrows(
                        com.github.dropguard.summer.core.exception.MissingWhereClauseException
                                .class,
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
        IllegalArgumentException ex =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                queryTemplate
                                        .update(Issue.class)
                                        .set("not_a_column", "x")
                                        .where(eq("id", 1))
                                        .execute());
        assertTrue(ex.getMessage().contains("Unknown column"));
    }

    // ── join / EXISTS (relationship queries) ─────────────────────────

    @Test
    void existsFiltersWithoutMultiplyingRootRows() {
        // Issue 1 has TWO tags (10 and 20); an EXISTS filter must not duplicate it.
        List<Issue> tagged =
                queryTemplate
                        .select(Issue.class)
                        .exists(
                                IssueTag.class,
                                "it",
                                and(eqCol("it.issue_id", "root.id"), eq("it.tag_id", 10)))
                        .limit(100)
                        .list();
        assertEquals(List.of(1, 2), tagged.stream().map(Issue::id).sorted().toList());
        // count() must count distinct root rows, not joined rows
        assertEquals(
                2L,
                queryTemplate
                        .select(Issue.class)
                        .exists(IssueTag.class, "it", eqCol("it.issue_id", "root.id"))
                        .count());
    }

    @Test
    void existsCombinesWithRootCriteria() {
        assertEquals(
                1L,
                queryTemplate
                        .select(Issue.class)
                        .where(eq("status", "OPEN"))
                        .exists(
                                IssueTag.class,
                                "it",
                                and(eqCol("it.issue_id", "root.id"), eq("it.tag_id", 20)))
                        .count());
        List<Issue> result =
                queryTemplate
                        .select(Issue.class)
                        .where(eq("status", "OPEN"))
                        .exists(
                                IssueTag.class,
                                "it",
                                and(eqCol("it.issue_id", "root.id"), eq("it.tag_id", 20)))
                        .limit(100)
                        .list();
        // Only issue 1 is OPEN and tagged 20.
        assertEquals(List.of(1), result.stream().map(Issue::id).toList());
    }

    @Test
    void joinBringsRelatedTableIntoFrom() {
        // join() is for 1:1/N:1 expansion; verify it emits a JOIN and validates
        // the ON predicate's qualified columns.
        assertEquals(
                3L,
                queryTemplate
                        .select(Issue.class)
                        .join(IssueTag.class, "it", eqCol("it.issue_id", "root.id"))
                        .count());
        List<Issue> result =
                queryTemplate
                        .select(Issue.class)
                        .join(IssueTag.class, "it", eqCol("it.issue_id", "root.id"))
                        .limit(100)
                        .list();
        // JOIN multiplies root rows by matches: issue 1 has tags 10,20 (2 rows),
        // issue 2 has tag 10 (1 row), issue 3 has none -> 3 rows total.
        assertEquals(3, result.size());
    }

    @Test
    void joinOnWithValueBindingAlignsParams() {
        // The ON clause binds a value (tag_id = 10); its `?` must receive the
        // bound value, not be left dangling (regression: ON params were dropped,
        // shifting every later binding).
        assertEquals(
                2L,
                queryTemplate
                        .select(Issue.class)
                        .join(
                                IssueTag.class,
                                "it",
                                and(eqCol("it.issue_id", "root.id"), eq("it.tag_id", 10)))
                        .count());
        List<Issue> result =
                queryTemplate
                        .select(Issue.class)
                        .join(
                                IssueTag.class,
                                "it",
                                and(eqCol("it.issue_id", "root.id"), eq("it.tag_id", 10)))
                        .limit(100)
                        .list();
        // Issues 1 and 2 are tagged 10; issue 3 has no tags.
        assertEquals(List.of(1, 2), result.stream().map(Issue::id).sorted().toList());
    }

    @Test
    void joinValueBindingCombinesWithWhereValueBinding() {
        // where(...) before join(...): the ON param must still precede the WHERE
        // param in the PreparedStatement (SQL text order: JOIN before WHERE).
        assertEquals(
                1L,
                queryTemplate
                        .select(Issue.class)
                        .where(eq("assignee", "alice"))
                        .join(
                                IssueTag.class,
                                "it",
                                and(eqCol("it.issue_id", "root.id"), eq("it.tag_id", 10)))
                        .count());
        List<Issue> result =
                queryTemplate
                        .select(Issue.class)
                        .where(eq("assignee", "alice"))
                        .join(
                                IssueTag.class,
                                "it",
                                and(eqCol("it.issue_id", "root.id"), eq("it.tag_id", 10)))
                        .limit(100)
                        .list();
        // Alice owns issues 1 and 3; only issue 1 is tagged 10.
        assertEquals(List.of(1), result.stream().map(Issue::id).toList());
    }

    @Test
    void multipleJoinsKeepParamsAligned() {
        // Two joins, each with a value-binding ON clause; both params must bind
        // to their own `?` regardless of alias map iteration order.
        assertEquals(
                1L,
                queryTemplate
                        .select(Issue.class)
                        .join(
                                IssueTag.class,
                                "t10",
                                and(eqCol("t10.issue_id", "root.id"), eq("t10.tag_id", 10)))
                        .join(
                                IssueTag.class,
                                "t20",
                                and(eqCol("t20.issue_id", "root.id"), eq("t20.tag_id", 20)))
                        .count());
        List<Issue> result =
                queryTemplate
                        .select(Issue.class)
                        .join(
                                IssueTag.class,
                                "t10",
                                and(eqCol("t10.issue_id", "root.id"), eq("t10.tag_id", 10)))
                        .join(
                                IssueTag.class,
                                "t20",
                                and(eqCol("t20.issue_id", "root.id"), eq("t20.tag_id", 20)))
                        .limit(100)
                        .list();
        // Only issue 1 carries both tags 10 and 20.
        assertEquals(List.of(1), result.stream().map(Issue::id).toList());
    }

    @Test
    void firstOverridesCallerLimit() {
        // Regression: .limit(n).first() must emit a single LIMIT (the old code
        // appended " LIMIT 1" after " LIMIT n" -> invalid SQL).
        Issue first = queryTemplate.select(Issue.class).orderBy("id").limit(2).first();
        assertEquals(1, first.id());

        Issue afterOffset = queryTemplate.select(Issue.class).orderBy("id").offset(1).first();
        assertEquals(2, afterOffset.id());
    }

    @Test
    void existsRejectsUnknownQualifiedColumn() {
        IllegalArgumentException ex =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                queryTemplate
                                        .select(Issue.class)
                                        .exists(IssueTag.class, "it", eqCol("it.bogus", "root.id"))
                                        .list());
        assertTrue(ex.getMessage().contains("Unknown column"));
    }

    @Test
    void existsRejectsUnknownTableAlias() {
        IllegalArgumentException ex =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> queryTemplate.select(Issue.class).where(eq("nope.col", 1)).list());
        assertTrue(ex.getMessage().contains("Unknown table alias"));
    }

    @Test
    void loadByForeignKeysGroupsChildrenByFkInOneQuery() {
        // Anti-N+1: load all tags for issues 1 and 2 in a single IN query, grouped by issue_id.
        // issue_tags rows: (1,10),(1,20),(2,10).
        Map<Object, List<IssueTag>> byIssue =
                queryTemplate.loadByForeignKeys(IssueTag.class, "issue_id", List.of(1, 2));
        assertEquals(Set.of(1, 2), byIssue.keySet());
        assertEquals(
                Set.of(10, 20),
                byIssue.get(1).stream()
                        .map(IssueTag::tagId)
                        .collect(java.util.stream.Collectors.toSet()));
        assertEquals(
                Set.of(10),
                byIssue.get(2).stream()
                        .map(IssueTag::tagId)
                        .collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void loadByForeignKeysWithEmptyKeysReturnsEmptyMapWithoutQuerying() {
        assertEquals(
                Map.of(), queryTemplate.loadByForeignKeys(IssueTag.class, "issue_id", List.of()));
    }

    @Test
    void loadByForeignKeysWithNullKeysReturnsEmptyMapWithoutQuerying() {
        assertEquals(Map.of(), queryTemplate.loadByForeignKeys(IssueTag.class, "issue_id", null));
    }

    @Test
    void loadByForeignKeysRejectsUnknownFkColumn() {
        IllegalArgumentException ex =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                queryTemplate.loadByForeignKeys(
                                        IssueTag.class, "bogus_fk", List.of(1)));
        assertTrue(ex.getMessage().contains("Unknown column"));
    }

    @Test
    void inDrivesBatchUpdateAcrossMatchedRows() {
        // UPDATE ... WHERE id IN (...) — batch mutation via the IN predicate.
        queryTemplate
                .update(Issue.class)
                .set("status", "CLOSED")
                .where(in("id", List.of(1, 3)))
                .execute();

        assertEquals(
                "CLOSED", queryTemplate.select(Issue.class).where(eq("id", 1)).first().status());
        assertEquals(
                "CLOSED", queryTemplate.select(Issue.class).where(eq("id", 3)).first().status());
        assertEquals("OPEN", queryTemplate.select(Issue.class).where(eq("id", 2)).first().status());
    }

    @Test
    void inDrivesBatchDeleteAcrossMatchedRows() {
        // DELETE ... WHERE id IN (...) — batch delete via the IN predicate.
        queryTemplate.delete(Issue.class).where(in("id", List.of(1, 2))).execute();

        assertEquals(1L, queryTemplate.select(Issue.class).count());
        assertEquals("Third", queryTemplate.select(Issue.class).first().title());
    }
}
