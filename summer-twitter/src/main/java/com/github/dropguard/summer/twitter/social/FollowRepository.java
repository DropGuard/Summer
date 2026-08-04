package com.github.dropguard.summer.twitter.social;

import com.github.dropguard.summer.core.Component;
import com.github.dropguard.summer.data.jdbc.JdbcTemplate;
import java.util.List;

@Component
public class FollowRepository {

    private final JdbcTemplate jdbcTemplate;

    public FollowRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Atomically insert if absent; returns true when a row was actually created. */
    public boolean insertIfAbsent(Follow follow) {
        String sql = "INSERT INTO follows (id, follower_id, following_id, created_at) VALUES (?, ?, ?, ?) ON CONFLICT DO NOTHING";
        int rows = jdbcTemplate.update(sql, follow.id(), follow.followerId(), follow.followingId(), follow.createdAt());
        return rows > 0;
    }

    /** Delete and return the number of rows actually removed (0 or 1). */
    public int deleteByUsers(Long followerId, Long followingId) {
        String sql = "DELETE FROM follows WHERE follower_id = ? AND following_id = ?";
        return jdbcTemplate.update(sql, followerId, followingId);
    }

    public List<Follow> findFollowers(Long followingId, Long cursor, int limit) {
        if (cursor == null) {
            String sql = "SELECT id, follower_id, following_id, created_at FROM follows WHERE following_id = ? ORDER BY id DESC LIMIT ?";
            return jdbcTemplate.queryForList(sql, Follow.class, followingId, limit);
        } else {
            String sql = "SELECT id, follower_id, following_id, created_at FROM follows WHERE following_id = ? AND id < ? ORDER BY id DESC LIMIT ?";
            return jdbcTemplate.queryForList(sql, Follow.class, followingId, cursor, limit);
        }
    }

    public List<Follow> findFollowing(Long followerId, Long cursor, int limit) {
        if (cursor == null) {
            String sql = "SELECT id, follower_id, following_id, created_at FROM follows WHERE follower_id = ? ORDER BY id DESC LIMIT ?";
            return jdbcTemplate.queryForList(sql, Follow.class, followerId, limit);
        } else {
            String sql = "SELECT id, follower_id, following_id, created_at FROM follows WHERE follower_id = ? AND id < ? ORDER BY id DESC LIMIT ?";
            return jdbcTemplate.queryForList(sql, Follow.class, followerId, cursor, limit);
        }
    }

    public List<Long> findInfluencerFollowing(Long followerId, int followerCountThreshold) {
        String sql = "SELECT f.following_id FROM follows f JOIN users u ON f.following_id = u.id WHERE f.follower_id = ? AND u.follower_count >= ?";
        return jdbcTemplate.queryForList(sql, Long.class, followerId, followerCountThreshold);
    }
}
