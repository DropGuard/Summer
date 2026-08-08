package com.github.dropguard.summer.twitter.social;

import com.github.dropguard.summer.core.Component;
import com.github.dropguard.summer.data.jdbc.JdbcTemplate;

@Component
public class LikeRepository {

    private final JdbcTemplate jdbcTemplate;

    public LikeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Atomically insert if absent; returns true when a row was actually created. */
    public boolean insertIfAbsent(Like like) {
        String sql =
                "INSERT INTO likes (id, user_id, tweet_id, created_at) VALUES (?, ?, ?, ?) ON"
                        + " CONFLICT DO NOTHING";
        int rows =
                jdbcTemplate.update(
                        sql, like.id(), like.userId(), like.tweetId(), like.createdAt());
        return rows > 0;
    }

    /** Delete and return the number of rows actually removed (0 or 1). */
    public int deleteByUserAndTweet(Long userId, Long tweetId) {
        String sql = "DELETE FROM likes WHERE user_id = ? AND tweet_id = ?";
        return jdbcTemplate.update(sql, userId, tweetId);
    }

    /** Delete all likes for a tweet — called before tweet deletion. */
    public void deleteByTweetId(Long tweetId) {
        String sql = "DELETE FROM likes WHERE tweet_id = ?";
        jdbcTemplate.update(sql, tweetId);
    }
}
