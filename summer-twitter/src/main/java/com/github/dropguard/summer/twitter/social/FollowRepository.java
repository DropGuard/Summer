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

    public void insert(Follow follow) {
        String sql = "INSERT INTO follows (id, follower_id, following_id, created_at) VALUES (?, ?, ?, ?)";
        jdbcTemplate.update(sql, follow.id(), follow.followerId(), follow.followingId(), follow.createdAt());
    }

    public void delete(Long followerId, Long followingId) {
        String sql = "DELETE FROM follows WHERE follower_id = ? AND following_id = ?";
        jdbcTemplate.update(sql, followerId, followingId);
    }

    public boolean exists(Long followerId, Long followingId) {
        String sql = "SELECT COUNT(*) FROM follows WHERE follower_id = ? AND following_id = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, followerId, followingId);
        return count != null && count > 0;
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

    public List<Long> findBigVFollowing(Long followerId, int followerCountThreshold) {
        String sql = "SELECT f.following_id FROM follows f JOIN users u ON f.following_id = u.id WHERE f.follower_id = ? AND u.follower_count >= ?";
        return jdbcTemplate.queryForList(sql, Long.class, followerId, followerCountThreshold);
    }
}
