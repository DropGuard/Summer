package com.github.dropguard.summer.tck.data.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.data.jdbc.JdbcTemplate;
import com.github.dropguard.summer.tck.invisible.fixtures.narrow.jdbc.CustomMappedType;
import com.github.dropguard.summer.tck.invisible.fixtures.narrow.jdbc.ProducerInitJdbcConfig;
import com.github.dropguard.summer.test.SummerTestExtension;
import com.github.dropguard.summer.test.annotation.DualEngine;
import com.github.dropguard.summer.test.annotation.SummerTest;
import java.util.List;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Behavioral regression for the {@code AotProductConstructor} rework: a {@code @Bean} producer's
 * own initialization must survive on BOTH engines. {@code CustomMappedType} is deliberately NOT a
 * {@code @RowModel} — the only mapper for it comes from the producer's own {@code registerMapper}
 * call — so a query that maps it proves the producer body ran. Before the rework the AOT engine
 * replaced the producer with a baked construction and the custom mapper was silently dropped.
 *
 * <p>The {@link BeanContainer} comes from the method parameter: {@code @DualEngine} shares one test
 * instance (whose injected fields are the RUNTIME container's beans), so the AOT invocation must
 * read from its own per-invocation container.
 */
@SummerTest
public class JdbcTemplateProducerInitDualEngineTest {

    @RegisterExtension
    static SummerTestExtension ext =
            SummerTestExtension.builder().beanClasses(ProducerInitJdbcConfig.class).build();

    @DualEngine
    void producerCustomInitSurvivesOnBothEngines(BeanContainer container) {
        JdbcTemplate jdbcTemplate = container.getBean(JdbcTemplate.class);
        List<CustomMappedType> rows =
                jdbcTemplate.queryForList("SELECT 1 AS id, 'x' AS name", CustomMappedType.class);
        assertEquals(
                List.of(new CustomMappedType(1, "x")),
                rows,
                "the producer's own registerMapper call must survive on both engines");
        // The container seal phase still freezes the template after assembly on both engines.
        assertThrows(
                IllegalStateException.class,
                () -> jdbcTemplate.registerMapper(CustomMappedType.class, (rs, rowNum) -> null));
    }
}
