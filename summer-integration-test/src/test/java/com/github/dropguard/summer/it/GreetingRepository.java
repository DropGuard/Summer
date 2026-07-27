package com.github.dropguard.summer.it;

import com.github.dropguard.summer.core.Component;
import com.github.dropguard.summer.data.jdbc.JdbcTemplate;
import java.util.List;

/**
 * Minimal repository backing {@link Greeting} — proves a {@code @SummerTest} universe can discover
 * and wire a real-JDBC component against a live Postgres.
 */
@Component
public class GreetingRepository {

    private final JdbcTemplate jdbcTemplate;

    public GreetingRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(Greeting greeting) {
        // Idempotent: both DI engines (Runtime + AOT) run against the SAME shared
        // Postgres, so the second run must not clash on the primary key.
        jdbcTemplate.update(
                "INSERT INTO greetings (id, text) VALUES (?, ?) "
                        + "ON CONFLICT (id) DO UPDATE SET text = EXCLUDED.text",
                greeting.id(),
                greeting.text());
    }

    public void delete(Long id) {
        jdbcTemplate.update("DELETE FROM greetings WHERE id = ?", id);
    }

    public Greeting findById(Long id) {
        return jdbcTemplate.queryForObject(
                "SELECT id, text FROM greetings WHERE id = ?", Greeting.class, id);
    }

    public List<Greeting> all() {
        return jdbcTemplate.queryForList(
                "SELECT id, text FROM greetings ORDER BY id", Greeting.class);
    }
}
