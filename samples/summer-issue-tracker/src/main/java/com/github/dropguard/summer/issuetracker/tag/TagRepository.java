package com.github.dropguard.summer.issuetracker.tag;

import com.github.dropguard.summer.core.Component;
import com.github.dropguard.summer.data.jdbc.JdbcTemplate;
import java.util.List;
import java.util.Optional;

@Component
public class TagRepository {

    private final JdbcTemplate jdbcTemplate;

    public TagRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(Tag tag) {
        String sql = "INSERT INTO tags (id, org_id, name, color) VALUES (?, ?, ?, ?)";
        jdbcTemplate.update(sql, tag.id(), tag.orgId(), tag.name(), tag.color());
    }

    public Optional<Tag> findById(Long id) {
        String sql = "SELECT id, org_id, name, color FROM tags WHERE id = ?";
        return Optional.ofNullable(jdbcTemplate.queryForObject(sql, Tag.class, id));
    }

    public Optional<Tag> findByName(Long orgId, String name) {
        String sql = "SELECT id, org_id, name, color FROM tags WHERE org_id = ? AND name = ?";
        return Optional.ofNullable(jdbcTemplate.queryForObject(sql, Tag.class, orgId, name));
    }

    public List<Tag> findByOrg(Long orgId) {
        String sql = "SELECT id, org_id, name, color FROM tags WHERE org_id = ? ORDER BY name";
        return jdbcTemplate.queryForList(sql, Tag.class, orgId);
    }

    public void attach(Long issueId, Long tagId) {
        String sql =
                "INSERT INTO issue_tags (issue_id, tag_id) VALUES (?, ?) ON CONFLICT DO NOTHING";
        jdbcTemplate.update(sql, issueId, tagId);
    }

    public void detach(Long issueId, Long tagId) {
        String sql = "DELETE FROM issue_tags WHERE issue_id = ? AND tag_id = ?";
        jdbcTemplate.update(sql, issueId, tagId);
    }

    public List<Long> tagIdsForIssue(Long issueId) {
        String sql = "SELECT tag_id FROM issue_tags WHERE issue_id = ?";
        return jdbcTemplate.queryForList(sql, Long.class, issueId);
    }
}
