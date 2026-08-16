package com.github.dropguard.summer.realworld.article;

import com.github.dropguard.summer.core.Component;
import com.github.dropguard.summer.data.jdbc.JdbcTemplate;
import java.util.LinkedHashSet;
import java.util.Set;

@Component
public class FavoriteRepository {
    private final JdbcTemplate jdbcTemplate;

    public FavoriteRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void favorite(Long userId, Long articleId) {
        jdbcTemplate.update(
                "INSERT INTO favorites (user_id, article_id) VALUES (?, ?)"
                        + " ON CONFLICT DO NOTHING",
                userId,
                articleId);
    }

    public void unfavorite(Long userId, Long articleId) {
        jdbcTemplate.update(
                "DELETE FROM favorites WHERE user_id = ? AND article_id = ?", userId, articleId);
    }

    /** Remove all favorites for an article — called when the article is deleted. */
    public void deleteByArticleId(Long articleId) {
        jdbcTemplate.update("DELETE FROM favorites WHERE article_id = ?", articleId);
    }

    public boolean isFavorited(Long userId, Long articleId) {
        Integer match =
                jdbcTemplate.queryForObject(
                        "SELECT 1 FROM favorites WHERE user_id = ? AND article_id = ?",
                        Integer.class,
                        userId,
                        articleId);
        return match != null;
    }

    public int countByArticleId(Long articleId) {
        Integer count =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM favorites WHERE article_id = ?",
                        Integer.class,
                        articleId);
        return count == null ? 0 : count;
    }

    public Set<Long> getArticleIdsFavoritedBy(Long userId) {
        return new LinkedHashSet<>(
                jdbcTemplate.queryForList(
                        "SELECT article_id FROM favorites WHERE user_id = ? ORDER BY article_id",
                        Long.class,
                        userId));
    }
}
