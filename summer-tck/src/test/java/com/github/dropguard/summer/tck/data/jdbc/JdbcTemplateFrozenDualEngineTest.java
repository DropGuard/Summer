package com.github.dropguard.summer.tck.data.jdbc;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.github.dropguard.summer.data.jdbc.JdbcTemplate;
import com.github.dropguard.summer.fixtures.data.jdbc.User;
import com.github.dropguard.summer.test.annotation.DualEngine;
import com.github.dropguard.summer.test.annotation.SummerTest;

/**
 * Post-assembly immutability contract for {@link JdbcTemplate}: {@code registerMapper} throws on
 * both engines once the container is built. On the Runtime engine the reflective registrar fills
 * the mappers during assembly and then seals the template; on the AOT engine the {@code
 * JdbcTemplateAotConstructor} bakes the mappers into an immutable construction (the user's
 * {@code @Bean} producer is bypassed), so the template is born sealed — a post-assembly {@code
 * registerMapper} that did NOT throw would prove the producer ran instead.
 *
 * <p>The end-to-end correctness of the baked mappers is covered by {@link QueryBuilderTCKTest},
 * which maps rows through the same universe on both engines.
 */
@SummerTest
class JdbcTemplateFrozenDualEngineTest {

    private final JdbcTemplate jdbcTemplate;

    JdbcTemplateFrozenDualEngineTest(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @DualEngine
    void registerMapperThrowsAfterAssembly() {
        assertThrows(
                IllegalStateException.class,
                () -> jdbcTemplate.registerMapper(User.class, (rs, rowNum) -> null));
    }
}
