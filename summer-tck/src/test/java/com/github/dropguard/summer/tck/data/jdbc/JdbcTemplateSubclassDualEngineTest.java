package com.github.dropguard.summer.tck.data.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.data.jdbc.JdbcTemplate;
import com.github.dropguard.summer.tck.invisible.fixtures.narrow.jdbc.RuntimeRowMapperNarrowFill;
import com.github.dropguard.summer.tck.invisible.fixtures.narrow.jdbc.SubclassJdbcConfig;
import com.github.dropguard.summer.tck.invisible.fixtures.narrow.jdbc.SubclassUser;
import com.github.dropguard.summer.tck.invisible.fixtures.narrow.jdbc.UserTemplate;
import com.github.dropguard.summer.test.SummerTestExtension;
import com.github.dropguard.summer.test.annotation.DualEngine;
import com.github.dropguard.summer.test.annotation.SummerTest;
import java.util.List;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Behavioral regression for the {@code AotProductConstructor} supertype-chain resolution: a
 * {@code @Bean} product whose declared type is a {@link JdbcTemplate} subclass must get the
 * {@code @RowModel} mappers on BOTH engines — the runtime engine via its assembly-time filler (the
 * seeded {@link RuntimeRowMapperNarrowFill}; on AOT the {@code @ConditionalOnBean(RuntimeDiMarker)}
 * gate drops it, the same gate the framework's real registrar relies on), the AOT engine via the
 * baked statements from the base type's provider. Before the fix the AOT engine found no provider
 * for the subclass and baked nothing.
 *
 * <p>The {@link BeanContainer} comes from the method parameter: {@code @DualEngine} shares one test
 * instance (whose injected fields are the RUNTIME container's beans), so the AOT invocation must
 * read from its own per-invocation container.
 */
@SummerTest
public class JdbcTemplateSubclassDualEngineTest {

    @RegisterExtension
    static SummerTestExtension ext =
            SummerTestExtension.builder()
                    .beanClasses(
                            SubclassJdbcConfig.class,
                            SubclassUser.class,
                            RuntimeRowMapperNarrowFill.class)
                    .build();

    @DualEngine
    void subclassProductMapsSeededRowModelOnBothEngines(BeanContainer container) {
        UserTemplate userTemplate = container.getBean(UserTemplate.class);
        List<SubclassUser> rows =
                userTemplate.queryForList("SELECT 1 AS id, 'x' AS name", SubclassUser.class);
        assertEquals(
                List.of(new SubclassUser(1, "x")),
                rows,
                "a subclass product must be mapped on both engines — on AOT via the base type's"
                        + " baked statements");
    }
}
