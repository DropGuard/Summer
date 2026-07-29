package com.github.dropguard.summer.issuetracker.issue;

import java.util.List;
import java.util.Optional;

import com.github.dropguard.summer.core.Component;
import com.github.dropguard.summer.data.jdbc.JdbcTemplate;
import com.github.dropguard.summer.data.jdbc.query.Criteria;
import com.github.dropguard.summer.data.jdbc.query.QueryBuilder;
import com.github.dropguard.summer.data.jdbc.query.QueryTemplate;
import com.github.dropguard.summer.issuetracker.comment.Comment;
import com.github.dropguard.summer.issuetracker.tag.Tag;

/**
 * Issue persistence. Two query styles are exercised here, both supported by
 * Summer's data-jdbc module:
 *
 * <ul>
 * <li>Hand-written SQL for association-heavy reads (tag join, comment count) —
 * values are always bound as {@code ?} parameters, so no injection surface.</li>
 * <li>{@link QueryTemplate}/{@link QueryBuilder} for the dynamic filter the UI
 * needs ("assigned to me + IN_PROGRESS + tag 'frontend' + HIGH priority"). This
 * is the demo's probe of Summer's type-safe criteria API.</li>
 * </ul>
 */
@Component
public class IssueRepository {

    private final JdbcTemplate jdbcTemplate;
    private final QueryTemplate queryTemplate;

    public IssueRepository(JdbcTemplate jdbcTemplate, QueryTemplate queryTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.queryTemplate = queryTemplate;
    }

    public void insert(Issue issue) {
        String sql = """
                INSERT INTO issues (id, project_id, issue_key, title, description, status, priority, assignee_id, reporter_id, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        jdbcTemplate.update(sql, issue.id(), issue.projectId(), issue.issueKey(), issue.title(),
                issue.description(), issue.status(), issue.priority(), issue.assigneeId(),
                issue.reporterId(), issue.createdAt(), issue.updatedAt());
    }

    public Optional<Issue> findById(Long id) {
        String sql = """
                SELECT id, project_id, issue_key, title, description, status, priority, assignee_id, reporter_id, created_at, updated_at
                FROM issues WHERE id = ?
                """;
        return Optional.ofNullable(jdbcTemplate.queryForObject(sql, Issue.class, id));
    }

    public Optional<Issue> findByKey(Long projectId, String issueKey) {
        String sql = """
                SELECT id, project_id, issue_key, title, description, status, priority, assignee_id, reporter_id, created_at, updated_at
                FROM issues WHERE project_id = ? AND issue_key = ?
                """;
        return Optional.ofNullable(jdbcTemplate.queryForObject(sql, Issue.class, projectId, issueKey));
    }

    public void updateMutable(Issue issue) {
        String sql = """
                UPDATE issues SET title = ?, description = ?, status = ?, priority = ?, assignee_id = ?, updated_at = ?
                WHERE id = ?
                """;
        jdbcTemplate.update(sql, issue.title(), issue.description(), issue.status(), issue.priority(),
                issue.assigneeId(), issue.updatedAt(), issue.id());
    }

    public void delete(Long id) {
        jdbcTemplate.update("DELETE FROM issues WHERE id = ?", id);
    }

    // ── Association reads (hand-written SQL) ───────────────────────────

    public List<Tag> findTags(Long issueId) {
        String sql = """
                SELECT t.id, t.org_id, t.name, t.color FROM tags t
                JOIN issue_tags it ON it.tag_id = t.id
                WHERE it.issue_id = ? ORDER BY t.name
                """;
        return jdbcTemplate.queryForList(sql, Tag.class, issueId);
    }

    public List<Comment> findComments(Long issueId) {
        String sql = """
                SELECT id, issue_id, author_id, body, created_at FROM comments
                WHERE issue_id = ? ORDER BY created_at ASC
                """;
        return jdbcTemplate.queryForList(sql, Comment.class, issueId);
    }

    public int countComments(Long issueId) {
        String sql = "SELECT COUNT(*) FROM comments WHERE issue_id = ?";
        Integer n = jdbcTemplate.queryForObject(sql, Integer.class, issueId);
        return n == null ? 0 : n;
    }

    /** Removes all tag associations for {@code issueId} (before issue deletion). */
    public void detachAllTags(Long issueId) {
        jdbcTemplate.update("DELETE FROM issue_tags WHERE issue_id = ?", issueId);
    }

    // ── Dynamic filter ─────────────────────────────────────────────────

    /**
     * Filtered issue list for one project.
     *
     * <p>
     * Single-entity conditions (assignee / status / priority / reporter / title)
     * flow through {@link QueryTemplate}/{@link QueryBuilder} — Summer's type-safe
     * criteria API. The tag condition is a many-to-many relationship living in the
     * {@code issue_tags} join table; it is expressed with {@link QueryBuilder}'s
     * {@code exists(...)} relationship predicate, which pushes the tag filter into
     * the SQL as a {@code WHERE EXISTS} sub-query. Using {@code EXISTS} (rather than
     * a {@code JOIN}) keeps the root issue rows from multiplying, so pagination and
     * the total count stay correct even when an issue carries several matching tags.
     * </p>
     */
    public List<Issue> search(Long projectId, IssueFilter filter) {
        return searchPage(projectId, filter, 0, Integer.MAX_VALUE).content();
    }

    /**
     * Same filtering as {@link #search} but bounded to a page and returning the
     * total match count (so the UI can render pagination). The tag many-to-many
     * condition is pushed into the SQL via {@code exists(...)}, so the page window
     * and the total both reflect the tag-filtered result set.
     */
    public Page<Issue> searchPage(Long projectId, IssueFilter filter, int offset, int limit) {
        QueryBuilder<Issue> qb = queryTemplate.select(Issue.class)
                .where(QueryTemplate.eq("project_id", projectId));

        if (filter.assigneeId() != null) {
            qb.where(QueryTemplate.eq("assignee_id", filter.assigneeId()));
        }
        if (filter.status() != null) {
            qb.where(QueryTemplate.eq("status", filter.status()));
        }
        if (filter.priority() != null) {
            qb.where(QueryTemplate.eq("priority", filter.priority()));
        }
        if (filter.reporterId() != null) {
            qb.where(QueryTemplate.eq("reporter_id", filter.reporterId()));
        }
        if (filter.titleContains() != null && !filter.titleContains().isBlank()) {
            qb.where(QueryTemplate.like("title", "%" + filter.titleContains() + "%"));
        }
        if (filter.tagId() != null) {
            // Many-to-many tag filter: EXISTS sub-query keeps pagination correct.
            qb.exists(IssueTag.class, "it",
                    QueryTemplate.and(QueryTemplate.eqCol("it.issue_id", "root.id"),
                            QueryTemplate.eq("it.tag_id", filter.tagId())));
        }
        qb.orderBy("updated_at").desc();

        long total = qb.count();
        List<Issue> candidates = qb.offset(offset).limit(limit).list();
        return Page.of(candidates, total, new PageRequest(offset / Math.max(limit, 1), limit));
    }
}
