package summer.issuetracker.project;

import java.util.List;
import java.util.Optional;

import summer.core.Component;
import summer.data.jdbc.JdbcTemplate;

@Component
public class ProjectRepository {

    private final JdbcTemplate jdbcTemplate;

    public ProjectRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(Project project) {
        String sql = """
                INSERT INTO projects (id, org_id, project_key, name, lead_user_id, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        jdbcTemplate.update(sql, project.id(), project.orgId(), project.projectKey(),
                project.name(), project.leadUserId(), project.createdAt());
    }

    public Optional<Project> findById(Long id) {
        String sql = "SELECT id, org_id, project_key, name, lead_user_id, created_at FROM projects WHERE id = ?";
        return Optional.ofNullable(jdbcTemplate.queryForObject(sql, Project.class, id));
    }

    public Optional<Project> findByKey(Long orgId, String projectKey) {
        String sql = "SELECT id, org_id, project_key, name, lead_user_id, created_at FROM projects WHERE org_id = ? AND project_key = ?";
        return Optional.ofNullable(jdbcTemplate.queryForObject(sql, Project.class, orgId, projectKey));
    }

    public Optional<Project> findByKey(String projectKey) {
        String sql = "SELECT id, org_id, project_key, name, lead_user_id, created_at FROM projects WHERE project_key = ?";
        return Optional.ofNullable(jdbcTemplate.queryForObject(sql, Project.class, projectKey));
    }

    public List<Project> findByOrg(Long orgId) {
        String sql = "SELECT id, org_id, project_key, name, lead_user_id, created_at FROM projects WHERE org_id = ? ORDER BY project_key";
        return jdbcTemplate.queryForList(sql, Project.class, orgId);
    }

    public List<Project> findByMember(Long userId) {
        String sql = """
                SELECT p.id, p.org_id, p.project_key, p.name, p.lead_user_id, p.created_at
                FROM projects p JOIN project_members pm ON pm.project_id = p.id
                WHERE pm.user_id = ? ORDER BY p.project_key
                """;
        return jdbcTemplate.queryForList(sql, Project.class, userId);
    }

    public void addMember(Long projectId, Long userId, String role) {
        String sql = "INSERT INTO project_members (project_id, user_id, role) VALUES (?, ?, ?)";
        jdbcTemplate.update(sql, projectId, userId, role);
    }

    public void setMemberRole(Long projectId, Long userId, String role) {
        String sql = "UPDATE project_members SET role = ? WHERE project_id = ? AND user_id = ?";
        jdbcTemplate.update(sql, role, projectId, userId);
    }

    public void removeMember(Long projectId, Long userId) {
        String sql = "DELETE FROM project_members WHERE project_id = ? AND user_id = ?";
        jdbcTemplate.update(sql, projectId, userId);
    }

    /**
     * Atomically allocates the next project-scoped issue sequence number. The
     * counter row is lazily initialized on first use, so no project setup step is
     * needed. The {@code UPDATE ... RETURNING} is a single atomic increment — two
     * concurrent creates can never receive the same number, which the previous
     * {@code count(*) + 1} approach could under a race.
     */
    public long nextIssueSeq(Long projectId) {
        jdbcTemplate.update(
                "INSERT INTO project_counters (project_id, issue_seq) VALUES (?, 0) ON CONFLICT (project_id) DO NOTHING",
                projectId);
        String sql = "UPDATE project_counters SET issue_seq = issue_seq + 1 WHERE project_id = ? RETURNING issue_seq";
        Long seq = jdbcTemplate.queryForObject(sql, Long.class, projectId);
        return seq == null ? 1L : seq;
    }
    public List<ProjectMember> findMembers(Long projectId) {
        String sql = "SELECT project_id, user_id, role FROM project_members WHERE project_id = ? ORDER BY user_id";
        return jdbcTemplate.queryForList(sql, ProjectMember.class, projectId);
    }

    public Optional<ProjectMember> findMember(Long projectId, Long userId) {
        String sql = "SELECT project_id, user_id, role FROM project_members WHERE project_id = ? AND user_id = ?";
        return Optional.ofNullable(jdbcTemplate.queryForObject(sql, ProjectMember.class, projectId, userId));
    }
}
