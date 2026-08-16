package com.github.dropguard.summer.data.jdbc.it;

import com.github.dropguard.summer.core.Component;
import com.github.dropguard.summer.data.jdbc.JdbcTemplate;
import com.github.dropguard.summer.data.jdbc.query.QueryTemplate;
import java.util.List;

/**
 * Repository over {@link Person} used by the framework's real-database integration contracts.
 * Exercises both the direct {@link JdbcTemplate} path and the fluent {@link QueryTemplate} builder
 * so the integration test asserts the full JDBC stack against a real Postgres.
 */
@Component
public class PersonRepository {

    private final JdbcTemplate jdbcTemplate;
    private final QueryTemplate queryTemplate;

    public PersonRepository(JdbcTemplate jdbcTemplate, QueryTemplate queryTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.queryTemplate = queryTemplate;
    }

    public void save(Person person) {
        jdbcTemplate.update(
                "INSERT INTO persons (id, name, age, status) VALUES (?, ?, ?, ?)",
                person.id(),
                person.name(),
                person.age(),
                person.status());
    }

    public Person findById(Long id) {
        return queryTemplate.select(Person.class).where(QueryTemplate.eq("id", id)).first();
    }

    public List<Person> findByStatus(String status) {
        // Explicit bound: this fixture returns up to 100 rows (the test data is small). A
        // repository method that silently returns "all rows" would be an unbounded scan.
        return queryTemplate
                .select(Person.class)
                .where(QueryTemplate.eq("status", status))
                .limit(100)
                .list();
    }

    public void updateStatus(Long id, String status) {
        queryTemplate
                .update(Person.class)
                .set("status", status)
                .where(QueryTemplate.eq("id", id))
                .execute();
    }

    public void delete(Long id) {
        queryTemplate.delete(Person.class).where(QueryTemplate.eq("id", id)).execute();
    }

    public long count() {
        return queryTemplate.select(Person.class).count();
    }
}
