package com.github.dropguard.summer.realworld.config;

import com.github.dropguard.summer.core.ApplicationRunner;
import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.core.Component;
import com.github.dropguard.summer.data.jdbc.JdbcTemplate;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Creates the database schema on startup by executing {@code schema.sql} from the classpath.
 *
 * <p>RealWorld owns its schema: the framework does not auto-run DDL, so this {@link
 * ApplicationRunner} executes the idempotent {@code CREATE TABLE IF NOT EXISTS} statements before
 * the HTTP server accepts traffic. The runner runs on every container boot — including the
 * {@code @SummerTest} universes built by the test harness — so both the demo process and the
 * integration tests converge on the same single source of truth.
 */
@Component
public class SchemaInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SchemaInitializer.class);
    private static final String SCHEMA_RESOURCE = "schema.sql";

    private final JdbcTemplate jdbcTemplate;

    public SchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(BeanContainer context) throws Exception {
        String schema = loadSchema();
        List<String> statements = splitStatements(schema);
        for (String statement : statements) {
            jdbcTemplate.update(statement);
        }
        log.info("[Summer] database schema initialized ({} statements)", statements.size());
    }

    private String loadSchema() throws IOException {
        try (InputStream in =
                SchemaInitializer.class.getClassLoader().getResourceAsStream(SCHEMA_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException(
                        "Cannot find " + SCHEMA_RESOURCE + " on the classpath");
            }
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader =
                    new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append('\n');
                }
            }
            return sb.toString();
        }
    }

    /** Splits a SQL script on semicolons, dropping empty/comment statements. */
    private List<String> splitStatements(String script) {
        List<String> statements = new ArrayList<>();
        for (String stmt : script.split(";")) {
            String trimmed = stmt.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("--")) {
                statements.add(trimmed);
            }
        }
        return statements;
    }
}
