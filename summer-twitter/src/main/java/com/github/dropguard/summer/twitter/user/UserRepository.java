package com.github.dropguard.summer.twitter.user;

import com.github.dropguard.summer.core.Component;
import com.github.dropguard.summer.data.jdbc.JdbcTemplate;
import com.github.dropguard.summer.twitter.infra.SnowflakeIdGenerator;

import java.util.Optional;

@Component
public class UserRepository {
    private final JdbcTemplate jdbcTemplate;
    private final SnowflakeIdGenerator idGenerator;

    public UserRepository(JdbcTemplate jdbcTemplate, SnowflakeIdGenerator idGenerator) {
        this.jdbcTemplate = jdbcTemplate;
        this.idGenerator = idGenerator;
    }

    public User insert(User user) {
        Long id = user.id() != null ? user.id() : idGenerator.nextId();
        User toInsert = new User(
            id,
            user.username(),
            user.displayName(),
            user.email(),
            user.passwordHash(),
            user.bio(),
            user.followerCount() != null ? user.followerCount() : 0,
            user.followingCount() != null ? user.followingCount() : 0,
            user.createdAt()
        );
        
        jdbcTemplate.update(
            "INSERT INTO users (id, username, display_name, email, password_hash, bio, follower_count, following_count, created_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
            toInsert.id(), toInsert.username(), toInsert.displayName(), toInsert.email(),
            toInsert.passwordHash(), toInsert.bio(), toInsert.followerCount(),
            toInsert.followingCount(), toInsert.createdAt()
        );
        return toInsert;
    }

    public Optional<User> findById(Long id) {
        User user = jdbcTemplate.queryForObject("SELECT * FROM users WHERE id = ?", User.class, id);
        return Optional.ofNullable(user);
    }

    public Optional<User> findByUsername(String username) {
        User user = jdbcTemplate.queryForObject("SELECT * FROM users WHERE username = ?", User.class, username);
        return Optional.ofNullable(user);
    }

    public void updateCounts(Long id, int followerDelta, int followingDelta) {
        jdbcTemplate.update(
            "UPDATE users SET follower_count = follower_count + ?, following_count = following_count + ? WHERE id = ?",
            followerDelta, followingDelta, id
        );
    }

    public void updateProfile(Long id, String displayName, String bio) {
        jdbcTemplate.update(
            "UPDATE users SET display_name = ?, bio = ? WHERE id = ?",
            displayName, bio, id
        );
    }
}