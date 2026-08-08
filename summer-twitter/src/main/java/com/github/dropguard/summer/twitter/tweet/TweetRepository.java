package com.github.dropguard.summer.twitter.tweet;

import com.github.dropguard.summer.core.Component;
import com.github.dropguard.summer.data.jdbc.JdbcTemplate;
import java.util.List;

@Component
public class TweetRepository {

    private final JdbcTemplate jdbcTemplate;

    public TweetRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(Tweet tweet) {
        String sql =
                "INSERT INTO tweets (id, author_id, content, type, parent_id, like_count,"
                    + " reply_count, retweet_count, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        jdbcTemplate.update(
                sql,
                tweet.id(),
                tweet.authorId(),
                tweet.content(),
                tweet.type(),
                tweet.parentId(),
                tweet.likeCount(),
                tweet.replyCount(),
                tweet.retweetCount(),
                tweet.createdAt());
    }

    public Tweet findById(Long id) {
        String sql =
                "SELECT id, author_id, content, type, parent_id, like_count, reply_count,"
                        + " retweet_count, created_at FROM tweets WHERE id = ?";
        return jdbcTemplate.queryForObject(sql, Tweet.class, id);
    }

    public void delete(Long id) {
        String sql = "DELETE FROM tweets WHERE id = ?";
        jdbcTemplate.update(sql, id);
    }

    public List<Tweet> getReplies(Long parentId, Long cursor, int limit) {
        if (cursor == null) {
            String sql =
                    "SELECT id, author_id, content, type, parent_id, like_count, reply_count,"
                        + " retweet_count, created_at FROM tweets WHERE parent_id = ? ORDER BY id"
                        + " DESC LIMIT ?";
            return jdbcTemplate.queryForList(sql, Tweet.class, parentId, limit);
        } else {
            String sql =
                    "SELECT id, author_id, content, type, parent_id, like_count, reply_count,"
                        + " retweet_count, created_at FROM tweets WHERE parent_id = ? AND id < ?"
                        + " ORDER BY id DESC LIMIT ?";
            return jdbcTemplate.queryForList(sql, Tweet.class, parentId, cursor, limit);
        }
    }

    public void updateLikeCount(Long id, int delta) {
        String sql = "UPDATE tweets SET like_count = GREATEST(0, like_count + ?) WHERE id = ?";
        jdbcTemplate.update(sql, delta, id);
    }

    public void updateReplyCount(Long id, int delta) {
        String sql = "UPDATE tweets SET reply_count = GREATEST(0, reply_count + ?) WHERE id = ?";
        jdbcTemplate.update(sql, delta, id);
    }

    public void incrementRetweetCount(Long id) {
        String sql = "UPDATE tweets SET retweet_count = retweet_count + 1 WHERE id = ?";
        jdbcTemplate.update(sql, id);
    }

    public List<Tweet> findByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        String sql =
                "SELECT id, author_id, content, type, parent_id, like_count, reply_count,"
                        + " retweet_count, created_at FROM tweets WHERE id IN ("
                        + placeholders
                        + ")";
        return jdbcTemplate.queryForList(sql, Tweet.class, ids.toArray());
    }

    /** Find all replies/retweets/quotes that reference the given parent tweet. */
    public List<Tweet> findByParentId(Long parentId) {
        String sql =
                "SELECT id, author_id, content, type, parent_id, like_count, reply_count,"
                        + " retweet_count, created_at FROM tweets WHERE parent_id = ?";
        return jdbcTemplate.queryForList(sql, Tweet.class, parentId);
    }
}
