package com.github.dropguard.summer.it;

import com.github.dropguard.summer.core.Component;
import com.github.dropguard.summer.data.jdbc.JdbcTemplate;
import com.github.dropguard.summer.data.jdbc.query.QueryTemplate;
import java.util.List;

/**
 * Repository backing {@link ItIssue} through QueryBuilder — proves the fluent builder works
 * end-to-end against a real Postgres on both DI engines.
 */
@Component
public class ItIssueQueryRepo {

    private final JdbcTemplate jdbcTemplate;
    private final QueryTemplate queryTemplate;

    public ItIssueQueryRepo(JdbcTemplate jdbcTemplate, QueryTemplate queryTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.queryTemplate = queryTemplate;
    }

    public void save(ItIssue issue) {
        queryTemplate.save(issue);
    }

    public ItIssue findById(Long id) {
        return queryTemplate.select(ItIssue.class).where(QueryTemplate.eq("id", id)).first();
    }

    public List<ItIssue> findByStatus(String status) {
        return queryTemplate.select(ItIssue.class).where(QueryTemplate.eq("status", status)).list();
    }

    public void setStatus(Long id, String status) {
        queryTemplate
                .update(ItIssue.class)
                .set("status", status)
                .where(QueryTemplate.eq("id", id))
                .execute();
    }

    public void delete(Long id) {
        queryTemplate.delete(ItIssue.class).where(QueryTemplate.eq("id", id)).execute();
    }

    public long count() {
        return queryTemplate.select(ItIssue.class).count();
    }
}
