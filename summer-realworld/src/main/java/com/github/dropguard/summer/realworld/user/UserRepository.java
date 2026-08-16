package com.github.dropguard.summer.realworld.user;

import com.github.dropguard.summer.core.Component;
import com.github.dropguard.summer.data.jdbc.JdbcTemplate;
import java.util.List;
import java.util.Optional;

@Component
public class UserRepository {
    private final JdbcTemplate jdbcTemplate;

    private static final String COLUMNS =
            "id, username, email, password, bio, image, created_at, updated_at";

    public UserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
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
