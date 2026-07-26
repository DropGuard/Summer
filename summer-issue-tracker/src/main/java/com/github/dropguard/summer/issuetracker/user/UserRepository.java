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

    public List<User> findByOrg(Long orgId) {
        String sql = """
                SELECT id, org_id, username, display_name, email, password_hash, role, created_at
                FROM users WHERE org_id = ? ORDER BY display_name
                """;
        return jdbcTemplate.queryForList(sql, User.class, orgId);
    }

    public List<User> findByProject(Long projectId) {
        String sql = """
                SELECT u.id, u.org_id, u.username, u.display_name, u.email, u.password_hash, u.role, u.created_at
                FROM users u JOIN project_members pm ON pm.user_id = u.id
                WHERE pm.project_id = ? ORDER BY u.display_name
                """;
        return jdbcTemplate.queryForList(sql, User.class, projectId);
    }

    public int countByOrg(Long orgId) {
        String sql = "SELECT COUNT(*) FROM users WHERE org_id = ?";
        Integer n = jdbcTemplate.queryForObject(sql, Integer.class, orgId);
        return n == null ? 0 : n;
    }
}
