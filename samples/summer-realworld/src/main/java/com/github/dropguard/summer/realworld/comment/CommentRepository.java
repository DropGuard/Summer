package com.github.dropguard.summer.realworld.comment;

import com.github.dropguard.summer.core.Component;
import com.github.dropguard.summer.data.jdbc.JdbcTemplate;
import java.util.List;
import java.util.Optional;

@Component
public class CommentRepository {
    private final JdbcTemplate jdbcTemplate;

    private static final String COLUMNS = "id, body, article_id, author_id, created_at, updated_at";

    public CommentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Comment save(Comment comment) {
        if (comment.id() == null) {
            Long id =
                    jdbcTemplate.queryForObject(
                            "INSERT INTO comments (body, article_id, author_id, created_at,"
                                    + " updated_at) VALUES (?, ?, ?, ?, ?) RETURNING id",
                            Long.class,
                            comment.body(),
                            comment.articleId(),
                            comment.authorId(),
                            comment.createdAt(),
                            comment.updatedAt());
            return new Comment(
                    id,
                    comment.body(),
                    comment.articleId(),
                    comment.authorId(),
                    comment.createdAt(),
                    comment.updatedAt());
        }
        jdbcTemplate.update(
                "UPDATE comments SET body = ?, article_id = ?, author_id = ?, created_at = ?,"
                        + " updated_at = ? WHERE id = ?",
                comment.body(),
                comment.articleId(),
                comment.authorId(),
                comment.createdAt(),
                comment.updatedAt(),
                comment.id());
        return comment;
    }

    public Optional<Comment> findById(Long id) {
        return Optional.ofNullable(
                jdbcTemplate.queryForObject(
                        "SELECT " + COLUMNS + " FROM comments WHERE id = ?", Comment.class, id));
    }

    public List<Comment> findByArticleId(Long articleId) {
        return jdbcTemplate.queryForList(
                "SELECT " + COLUMNS + " FROM comments WHERE article_id = ? ORDER BY created_at ASC",
                Comment.class,
                articleId);
    }

    public List<Comment> findAll() {
        return jdbcTemplate.queryForList("SELECT " + COLUMNS + " FROM comments", Comment.class);
    }

    public void deleteById(Long id) {
        jdbcTemplate.update("DELETE FROM comments WHERE id = ?", id);
    }

    /** Remove all comments for an article — called when the article is deleted. */
    public void deleteByArticleId(Long articleId) {
        jdbcTemplate.update("DELETE FROM comments WHERE article_id = ?", articleId);
    }
}
