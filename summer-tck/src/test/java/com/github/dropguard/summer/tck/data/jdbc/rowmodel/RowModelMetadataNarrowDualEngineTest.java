package com.github.dropguard.summer.tck.data.jdbc.rowmodel;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.dropguard.summer.tck.negative.fixtures.narrow.rowmodel.EntityMetadataNarrowConfig;
import com.github.dropguard.summer.tck.negative.fixtures.narrow.rowmodel.RowModelEntity;
import com.github.dropguard.summer.data.jdbc.EntityMetadataRegistry;
import com.github.dropguard.summer.test.SummerTestExtension;
import com.github.dropguard.summer.test.annotation.DualEngine;
import com.github.dropguard.summer.test.annotation.SummerTest;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Behavioral regression: a {@code @RowModel} class seeded into the universe must be visible to
 * {@link EntityMetadataRegistry} on BOTH engines. The Runtime engine scans its discovery view at
 * build time; the AOT engine bakes the metadata at code-generation time from the same discovery
 * view — never a runtime-reconstructed, production-only index. Before the fix the AOT leg silently
 * missed the seeded entity and this test failed. The seeded config/entity live in the aot-engine's
 * {@code aot.narrow} package (excluded from its jandex.idx) so the tck's whole-universe tests never
 * see them.
 */
@SummerTest
public class RowModelMetadataNarrowDualEngineTest {

    @RegisterExtension
    static SummerTestExtension ext =
            SummerTestExtension.builder()
                    .beanClasses(EntityMetadataNarrowConfig.class, RowModelEntity.class)
                    .build();

    private final EntityMetadataRegistry registry;

    public RowModelMetadataNarrowDualEngineTest(EntityMetadataRegistry registry) {
        this.registry = registry;
    }

    @DualEngine
    void seededRowModelVisibleOnBothEngines() {
        assertTrue(
                registry.contains(RowModelEntity.class),
                "a seeded @RowModel must be visible to EntityMetadataRegistry on both engines");
    }
}
