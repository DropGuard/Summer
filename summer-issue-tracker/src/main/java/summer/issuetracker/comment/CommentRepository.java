package summer.issuetracker.comment;

import java.util.List;
import java.util.Optional;

import summer.core.Component;
import summer.data.jdbc.JdbcTemplate;

@Component
public class CommentRepository {

    private final JdbcTemplate jdbcTemplate;

    public CommentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(Comment comment) {
        String sql = "INSERT INTO comments (id, issue_id, author_id, body, created_at) VALUES (?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql, comment.id(), comment.issueId(), comment.authorId(), comment.body(), comment.createdAt());
    }

    public Optional<Comment> findById(Long id) {
        String sql = "SELECT id, issue_id, author_id, body, created_at FROM comments WHERE id = ?";
        return Optional.ofNullable(jdbcTemplate.queryForObject(sql, Comment.class, id));
    }

    public List<Comment> findByIssue(Long issueId) {
        String sql = "SELECT id, issue_id, author_id, body, created_at FROM comments WHERE issue_id = ? ORDER BY created_at ASC";
        return jdbcTemplate.queryForList(sql, Comment.class, issueId);
    }

    public void delete(Long id) {
        jdbcTemplate.update("DELETE FROM comments WHERE id = ?", id);
    }
}
