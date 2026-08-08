package com.github.dropguard.summer.issuetracker.comment;

import com.github.dropguard.summer.core.Component;
import com.github.dropguard.summer.data.jdbc.JdbcTemplate;
import java.util.List;

@Component
public class CommentRepository {

    private final JdbcTemplate jdbcTemplate;

    public CommentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(Comment comment) {
        String sql =
                "INSERT INTO comments (id, issue_id, author_id, body, created_at) VALUES (?, ?, ?,"
                        + " ?, ?)";
        jdbcTemplate.update(
                sql,
                comment.id(),
                comment.issueId(),
                comment.authorId(),
                comment.body(),
                comment.createdAt());
    }

    public List<Comment> findByIssue(Long issueId) {
        String sql =
                "SELECT id, issue_id, author_id, body, created_at FROM comments WHERE issue_id = ?"
                        + " ORDER BY created_at ASC";
        return jdbcTemplate.queryForList(sql, Comment.class, issueId);
    }

    /** Deletes all comments for an issue (called before issue deletion). */
    public void deleteByIssue(Long issueId) {
        jdbcTemplate.update("DELETE FROM comments WHERE issue_id = ?", issueId);
    }
}
