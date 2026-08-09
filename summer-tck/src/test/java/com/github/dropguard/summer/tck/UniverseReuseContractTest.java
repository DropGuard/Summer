package com.github.dropguard.summer.tck;

import static org.junit.jupiter.api.Assertions.assertSame;

import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.core.Engine;
import com.github.dropguard.summer.test.annotation.DualEngine;
import com.github.dropguard.summer.test.annotation.SummerTest;

/**
 * The universe-reuse guarantees through the REAL extension path (complementing the direct unit test
 * in summer-test): the test instance is built by the class-level SummerExtension (a RUNTIME
 * universe), and each {@code @DualEngine} invocation's beforeTestExecution acquires the universe
 * again — the same key must return the SAME container.
 *
 * <ul>
 *   <li>RUNTIME invocation: its container must BE the instance's injected container (the documented
 *       "the RUNTIME invocation reuses the universe SummerExtension built").
 *   <li>AOT invocation: its container must be the same across the class's methods (the AOT
 *       invocation reuses its own universe — the same key, no profile, no mocks).
 * </ul>
 *
 * <p>The assertion is order-independent: a static first-seen record, not a count (the JUnit
 * template invocations' order is hash-based).
 */
@SummerTest
public class UniverseReuseContractTest {

    private final BeanContainer injectedContainer;
    private static BeanContainer aotContainer;

    public UniverseReuseContractTest(BeanContainer injectedContainer) {
        this.injectedContainer = injectedContainer;
    }

    @DualEngine
    void first(BeanContainer container) {
        assertReuse(container);
    }

    @DualEngine
    void second(BeanContainer container) {
        assertReuse(container);
    }

    @DualEngine
    void third(BeanContainer container) {
        assertReuse(container);
    }

    private void assertReuse(BeanContainer container) {
        if (container.engine() == Engine.AOT) {
            if (aotContainer != null) {
                assertSame(
                        aotContainer,
                        container,
                        "the AOT invocation must reuse its universe across the class's methods");
            }
            aotContainer = container;
        } else {
            assertSame(
                    injectedContainer,
                    container,
                    "the RUNTIME invocation must reuse the universe the SummerExtension built");
        }
    }
}
