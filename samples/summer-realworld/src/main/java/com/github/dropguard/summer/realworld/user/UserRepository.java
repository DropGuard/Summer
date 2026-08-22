package com.github.dropguard.summer.realworld.user;

import com.github.dropguard.summer.core.Component;
import com.github.dropguard.summer.data.jdbc.JdbcTemplate;
import com.github.dropguard.summer.data.jdbc.query.QueryTemplate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Component
public class UserRepository {
    private final JdbcTemplate jdbcTemplate;
    private final QueryTemplate queryTemplate;

    private static final String COLUMNS =
            "id, username, email, password, bio, image, created_at, updated_at";

    public UserRepository(JdbcTemplate jdbcTemplate, QueryTemplate queryTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.queryTemplate = queryTemplate;
    }

    public User save(User user) {
        if (user.id() == null) {
            Long id =
                    jdbcTemplate.queryForObject(
                            "INSERT INTO users (username, email, password, bio, image,"
                                    + " created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)"
                                    + " RETURNING id",
                            Long.class,
                            user.username(),
                            user.email(),
                            user.password(),
                            user.bio(),
                            user.image(),
                            user.createdAt(),
                            user.updatedAt());
            return new User(
                    id,
                    user.username(),
                    user.email(),
                    user.password(),
                    user.bio(),
                    user.image(),
                    user.createdAt(),
                    user.updatedAt());
        }
        jdbcTemplate.update(
                "UPDATE users SET username = ?, email = ?, password = ?, bio = ?, image = ?,"
                        + " created_at = ?, updated_at = ? WHERE id = ?",
                user.username(),
                user.email(),
                user.password(),
                user.bio(),
                user.image(),
                user.createdAt(),
                user.updatedAt(),
                user.id());
        return user;
    }

    public Optional<User> findById(Long id) {
        return Optional.ofNullable(
                jdbcTemplate.queryForObject(
                        "SELECT " + COLUMNS + " FROM users WHERE id = ?", User.class, id));
    }

    /**
     * Batch-loads users by a set of ids in a single {@code IN} query — the anti-N+1 counterpart of
     * {@link #findById}. Callers assembling a list (e.g. resolving the author of every article in a
     * feed) load all users in one query instead of looping {@code findById} N times.
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

    public Optional<User> findByEmail(String email) {
        return Optional.ofNullable(
                jdbcTemplate.queryForObject(
                        "SELECT " + COLUMNS + " FROM users WHERE email = ?", User.class, email));
    }

    public Optional<User> findByUsername(String username) {
        return Optional.ofNullable(
                jdbcTemplate.queryForObject(
                        "SELECT " + COLUMNS + " FROM users WHERE username = ?",
                        User.class,
                        username));
    }

    public List<User> findAll() {
        return jdbcTemplate.queryForList("SELECT " + COLUMNS + " FROM users", User.class);
    }

    public void deleteById(Long id) {
        jdbcTemplate.update("DELETE FROM users WHERE id = ?", id);
    }
}
