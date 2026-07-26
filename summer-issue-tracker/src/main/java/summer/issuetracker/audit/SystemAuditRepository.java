package summer.issuetracker.audit;

import java.util.List;

import summer.core.Component;
import summer.data.jdbc.JdbcTemplate;

@Component
public class SystemAuditRepository {

    private final JdbcTemplate jdbcTemplate;

    public SystemAuditRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(SystemAudit event) {
        String sql = """
                INSERT INTO audit_events (id, org_id, actor_id, action, target_type, target_id, target_key, occurred_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        jdbcTemplate.update(sql, event.id(), event.orgId(), event.actorId(), event.action(),
                event.targetType(), event.targetId(), event.targetKey(), event.occurredAt());
    }

    /** All system audit events for an org, newest first. Survives deletion of the
     *  described entities — that is the whole point of a system audit log. */
    public List<SystemAudit> findByOrg(Long orgId) {
        String sql = "SELECT id, org_id, actor_id, action, target_type, target_id, target_key, occurred_at "
                + "FROM audit_events WHERE org_id = ? ORDER BY occurred_at DESC";
        return jdbcTemplate.queryForList(sql, SystemAudit.class, orgId);
    }
}
