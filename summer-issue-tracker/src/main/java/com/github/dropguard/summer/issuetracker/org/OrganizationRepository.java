package com.github.dropguard.summer.issuetracker.org;

import java.util.List;
import java.util.Optional;

import com.github.dropguard.summer.core.Component;
import com.github.dropguard.summer.data.jdbc.JdbcTemplate;

@Component
public class OrganizationRepository {

    private final JdbcTemplate jdbcTemplate;

    public OrganizationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(Organization org) {
        jdbcTemplate.update(
                "INSERT INTO organizations (id, name, slug, created_at) VALUES (?, ?, ?, ?)",
                org.id(), org.name(), org.slug(), org.createdAt());
    }

    /** Insert-or-skip: if the slug already exists the row is not created (idempotent). */
    public void insertOrIgnore(Organization org) {
        jdbcTemplate.update(
                "INSERT INTO organizations (id, name, slug, created_at) VALUES (?, ?, ?, ?) ON CONFLICT (slug) DO NOTHING",
                org.id(), org.name(), org.slug(), org.createdAt());
    }

    public Optional<Organization> findById(Long id) {
        String sql = "SELECT id, name, slug, created_at FROM organizations WHERE id = ?";
        return Optional.ofNullable(jdbcTemplate.queryForObject(sql, Organization.class, id));
    }

    public Optional<Organization> findBySlug(String slug) {
        String sql = "SELECT id, name, slug, created_at FROM organizations WHERE slug = ?";
        return Optional.ofNullable(jdbcTemplate.queryForObject(sql, Organization.class, slug));
    }

    public List<Organization> findAll() {
        String sql = "SELECT id, name, slug, created_at FROM organizations ORDER BY name";
        return jdbcTemplate.queryForList(sql, Organization.class);
    }
}
