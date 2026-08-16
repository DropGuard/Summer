package com.github.dropguard.summer.tck.di;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.dropguard.summer.fixtures.di.PostConstructFixture;
import com.github.dropguard.summer.test.annotation.DualEngine;
import com.github.dropguard.summer.test.annotation.SummerTest;

/**
 * Dual-engine contract for the {@code @PostConstruct} lifecycle boundary: the callback must have
 * run during assembly and the bean must be sealed (mutators throw) by the time it is injectable —
 * on the Runtime engine via reflection, on the AOT engine via the generated call in {@code wire()}.
 */
@SummerTest
class PostConstructDualEngineTest {

    private final PostConstructFixture fixture;

    PostConstructDualEngineTest(PostConstructFixture fixture) {
        this.fixture = fixture;
    }

    @DualEngine
    void postConstructRanAndBeanIsSealed() {
        assertEquals(
                42, fixture.initializedValue(), "@PostConstruct must have run during assembly");
        assertTrue(fixture.isFrozen(), "bean must be sealed after assembly");
        assertThrows(IllegalStateException.class, fixture::mutate);
    }
}
