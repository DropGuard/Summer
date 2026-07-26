package com.github.dropguard.summer.issuetracker.audit;

import java.util.List;

import com.github.dropguard.summer.core.Component;
import com.github.dropguard.summer.data.jdbc.JdbcTemplate;

@Component
public class IssueHistoryRepository {

    private final JdbcTemplate jdbcTemplate;

    public IssueHistoryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(IssueHistory entry) {
        String sql = """
                INSERT INTO issue_history (id, issue_id, actor_id, action, from_value, to_value, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        jdbcTemplate.update(sql, entry.id(), entry.issueId(), entry.actorId(), entry.action(),
                entry.fromValue(), entry.toValue(), entry.createdAt());
    }

    public List<IssueHistory> findByIssue(Long issueId) {
        String sql = "SELECT id, issue_id, actor_id, action, from_value, to_value, created_at FROM issue_history WHERE issue_id = ? ORDER BY created_at ASC";
        return jdbcTemplate.queryForList(sql, IssueHistory.class, issueId);
    }
}
