package com.github.dropguard.summer.issuetracker.user;

import java.util.List;
import java.util.Optional;

import com.github.dropguard.summer.core.Component;
import com.github.dropguard.summer.data.jdbc.JdbcTemplate;

@Component
public class UserRepository {

    private final JdbcTemplate jdbcTemplate;

    public UserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(User user) {
        String sql = """
                INSERT INTO users (id, org_id, username, display_name, email, password_hash, role, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        jdbcTemplate.update(sql, user.id(), user.orgId(), user.username(), user.displayName(),
                user.email(), user.passwordHash(), user.role(), user.createdAt());
    }

    public Optional<User> findById(Long id) {
        String sql = """
                SELECT id, org_id, username, display_name, email, password_hash, role, created_at
                FROM users WHERE id = ?
                """;
        return Optional.ofNullable(jdbcTemplate.queryForObject(sql, User.class, id));
    }

    public Optional<User> findByUsername(String username) {
        String sql = """
                SELECT id, org_id, username, display_name, email, password_hash, role, created_at
                FROM users WHERE username = ?
                """;
        return Optional.ofNullable(jdbcTemplate.queryForObject(sql, User.class, username));
    }

    public int countByOrg(Long orgId) {
        String sql = "SELECT COUNT(*) FROM users WHERE org_id = ?";
        Integer n = jdbcTemplate.queryForObject(sql, Integer.class, orgId);
        return n == null ? 0 : n;
    }
}
