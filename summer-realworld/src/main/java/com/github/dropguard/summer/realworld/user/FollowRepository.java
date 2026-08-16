package com.github.dropguard.summer.realworld.user;

import com.github.dropguard.summer.core.Component;
import com.github.dropguard.summer.data.jdbc.JdbcTemplate;
import java.util.LinkedHashSet;
import java.util.Set;

@Component
public class FollowRepository {
    private final JdbcTemplate jdbcTemplate;

    public FollowRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void follow(Long followerId, Long followeeId) {
        jdbcTemplate.update(
                "INSERT INTO follows (follower_id, followee_id) VALUES (?, ?)"
                        + " ON CONFLICT DO NOTHING",
                followerId,
                followeeId);
    }

    public void unfollow(Long followerId, Long followeeId) {
        jdbcTemplate.update(
                "DELETE FROM follows WHERE follower_id = ? AND followee_id = ?",
                followerId,
                followeeId);
    }

    public boolean isFollowing(Long followerId, Long followeeId) {
        Integer match =
                jdbcTemplate.queryForObject(
                        "SELECT 1 FROM follows WHERE follower_id = ? AND followee_id = ?",
                        Integer.class,
                        followerId,
                        followeeId);
        return match != null;
    }

    public Set<Long> getFollowing(Long followerId) {
        return new LinkedHashSet<>(
                jdbcTemplate.queryForList(
                        "SELECT followee_id FROM follows WHERE follower_id = ? ORDER BY"
                                + " followee_id",
                        Long.class,
                        followerId));
    }
}
