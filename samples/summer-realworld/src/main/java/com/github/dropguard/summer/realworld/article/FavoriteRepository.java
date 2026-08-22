package com.github.dropguard.summer.realworld.article;

import com.github.dropguard.summer.core.Component;
import com.github.dropguard.summer.data.jdbc.JdbcTemplate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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

    /**
     * Batch favorite counts for a set of articles in one query, keyed by article id — the anti-N+1
     * counterpart of {@link #countByArticleId} for a list response (one query instead of one per
     * article). Articles with no favorites are absent from the map.
     */
    public Map<Long, Integer> countByArticleIds(java.util.Collection<Long> articleIds) {
        if (articleIds == null || articleIds.isEmpty()) {
            return Map.of();
        }
        String sql =
                "SELECT article_id, COUNT(*) FROM favorites WHERE article_id IN ("
                        + String.join(",", java.util.Collections.nCopies(articleIds.size(), "?"))
                        + ") GROUP BY article_id";
        Map<Long, Integer> counts = new java.util.HashMap<>();
        List<Object[]> rows =
                jdbcTemplate.queryForList(
                        sql,
                        (rs, rowNum) -> new Object[] {rs.getObject(1), rs.getObject(2)},
                        articleIds.toArray());
        for (Object[] row : rows) {
            counts.put(((Number) row[0]).longValue(), ((Number) row[1]).intValue());
        }
        return counts;
    }

    /**
     * The set of article ids in {@code articleIds} that {@code userId} has favorited — a single
     * batch query, the anti-N+1 counterpart of {@link #isFavorited} for a list response.
     */
    public Set<Long> getFavoritedByUser(Long userId, java.util.Collection<Long> articleIds) {
        if (articleIds == null || articleIds.isEmpty()) {
            return Set.of();
        }
        String sql =
                "SELECT article_id FROM favorites WHERE user_id = ? AND article_id IN ("
                        + String.join(",", java.util.Collections.nCopies(articleIds.size(), "?"))
                        + ")";
        Object[] args = new Object[articleIds.size() + 1];
        args[0] = userId;
        int i = 1;
        for (Object id : articleIds) {
            args[i++] = id;
        }
        return new LinkedHashSet<>(jdbcTemplate.queryForList(sql, Long.class, args));
    }

    public Set<Long> getArticleIdsFavoritedBy(Long userId) {
        return new LinkedHashSet<>(
                jdbcTemplate.queryForList(
                        "SELECT article_id FROM favorites WHERE user_id = ? ORDER BY article_id",
                        Long.class,
                        userId));
    }
}
