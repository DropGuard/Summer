package com.github.dropguard.summer.twitter.social;

import com.github.dropguard.summer.core.Component;
import com.github.dropguard.summer.data.jdbc.JdbcTemplate;
import java.util.List;

@Component
public class LikeRepository {

    private final JdbcTemplate jdbcTemplate;

    public LikeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(Like like) {
        String sql = "INSERT INTO likes (id, user_id, tweet_id, created_at) VALUES (?, ?, ?, ?)";
        jdbcTemplate.update(sql, like.id(), like.userId(), like.tweetId(), like.createdAt());
    }

    public void delete(Long userId, Long tweetId) {
        String sql = "DELETE FROM likes WHERE user_id = ? AND tweet_id = ?";
        jdbcTemplate.update(sql, userId, tweetId);
    }

    public boolean exists(Long userId, Long tweetId) {
        String sql = "SELECT COUNT(*) FROM likes WHERE user_id = ? AND tweet_id = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, userId, tweetId);
        return count != null && count > 0;
    }
}
