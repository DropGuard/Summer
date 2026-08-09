package com.github.dropguard.summer.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.core.Engine;
import org.junit.jupiter.api.Test;

/**
 * The cache-reuse contract, tested directly against the lifecycle (no JUnit-extension ceremony):
 * the same {@link UniverseKey} (same test class + engine + mocks + overrides) must return the SAME
 * container instance — reuse is observable as container identity, and the second acquisition is
 * exactly one cache hit. Each test uses its own fixture so the JVM-wide counter deltas cannot
 * interfere across tests.
 */
class UniverseCacheReuseTest {

    /** No-arg fixtures: the whole-universe build has nothing to inject. */
    static final class FixtureA {}

    static final class FixtureB {}

    static final class FixtureC {}

    @Test
    void sameKeyReturnsSameContainer() throws Exception {
        long before = SummerTestLifecycle.instance().cacheHits();
        BeanContainer first =
                SummerTestLifecycle.createUniverse(FixtureA.class, Engine.RUNTIME).container();
        BeanContainer second =
                SummerTestLifecycle.createUniverse(FixtureA.class, Engine.RUNTIME).container();
        assertSame(first, second, "the same key must reuse the container");
        assertEquals(
                1,
                SummerTestLifecycle.instance().cacheHits() - before,
                "one extra hit for the reuse");
        first.close();
    }

    @Test
    void differentTestClassGetsDifferentContainer() throws Exception {
        long before = SummerTestLifecycle.instance().cacheHits();
        BeanContainer b1 =
                SummerTestLifecycle.createUniverse(FixtureB.class, Engine.RUNTIME).container();
        BeanContainer c1 =
                SummerTestLifecycle.createUniverse(FixtureC.class, Engine.RUNTIME).container();
        assertSame(
                b1,
                SummerTestLifecycle.createUniverse(FixtureB.class, Engine.RUNTIME).container(),
                "FixtureB's universe is reused");
        assertEquals(
                1,
                SummerTestLifecycle.instance().cacheHits() - before,
                "only FixtureB's reuse is a hit");
        b1.close();
        c1.close();
    }
}
