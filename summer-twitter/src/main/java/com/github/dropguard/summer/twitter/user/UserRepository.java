package com.github.dropguard.summer.twitter.user;

import com.github.dropguard.summer.core.Component;
import com.github.dropguard.summer.data.jdbc.JdbcTemplate;
import com.github.dropguard.summer.data.jdbc.query.QueryTemplate;
import com.github.dropguard.summer.twitter.infra.SnowflakeIdGenerator;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Component
public class UserRepository {
    private final JdbcTemplate jdbcTemplate;
    private final QueryTemplate queryTemplate;
    private final SnowflakeIdGenerator idGenerator;

    public UserRepository(
            JdbcTemplate jdbcTemplate,
            QueryTemplate queryTemplate,
            SnowflakeIdGenerator idGenerator) {
        this.jdbcTemplate = jdbcTemplate;
        this.queryTemplate = queryTemplate;
        this.idGenerator = idGenerator;
    }

    public User insert(User user) {
        Long id = user.id() != null ? user.id() : idGenerator.nextId();
        User toInsert =
                new User(
                        id,
                        user.username(),
                        user.displayName(),
                        user.email(),
                        user.passwordHash(),
                        user.bio(),
                        user.followerCount() != null ? user.followerCount() : 0,
                        user.followingCount() != null ? user.followingCount() : 0,
                        user.createdAt());

        jdbcTemplate.update(
                "INSERT INTO users (id, username, display_name, email, password_hash, bio,"
                    + " follower_count, following_count, created_at) VALUES (?, ?, ?, ?, ?, ?, ?,"
                    + " ?, ?)",
                toInsert.id(),
                toInsert.username(),
                toInsert.displayName(),
                toInsert.email(),
                toInsert.passwordHash(),
                toInsert.bio(),
                toInsert.followerCount(),
                toInsert.followingCount(),
                toInsert.createdAt());
        return toInsert;
    }

    public Optional<User> findById(Long id) {
        User user = jdbcTemplate.queryForObject("SELECT * FROM users WHERE id = ?", User.class, id);
        return Optional.ofNullable(user);
    }

    /**
     * Batch-loads users by a set of ids in a single {@code IN} query — the anti-N+1 counterpart of
     * {@link #findById}. Callers assembling a list (e.g. resolving the other party of every
     * conversation) load all users in one query instead of looping {@code findById} N times.
     */
    public List<User> findByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return queryTemplate
                .select(User.class)
                .where(QueryTemplate.in("id", ids))
                .orderBy("id")
                .limit(ids.size())
                .list();
    }

    public Optional<User> findByUsername(String username) {
        User user =
                jdbcTemplate.queryForObject(
                        "SELECT * FROM users WHERE username = ?", User.class, username);
        return Optional.ofNullable(user);
    }

    public Optional<User> findByEmail(String email) {
        User user =
                jdbcTemplate.queryForObject(
                        "SELECT * FROM users WHERE email = ?", User.class, email);
        return Optional.ofNullable(user);
    }

    public void updateCounts(Long id, int followerDelta, int followingDelta) {
        jdbcTemplate.update(
                "UPDATE users SET follower_count = GREATEST(0, follower_count + ?), following_count"
                        + " = GREATEST(0, following_count + ?) WHERE id = ?",
                followerDelta,
                followingDelta,
                id);
    }

    public void updateProfile(Long id, String displayName, String bio) {
        jdbcTemplate.update(
                "UPDATE users SET display_name = ?, bio = ? WHERE id = ?", displayName, bio, id);
    }
}
