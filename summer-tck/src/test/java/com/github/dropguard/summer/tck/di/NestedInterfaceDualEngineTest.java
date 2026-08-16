package com.github.dropguard.summer.tck.di;

import static org.junit.jupiter.api.Assertions.assertFalse;

import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.tck.invisible.fixtures.narrow.nested.NestedHolder;
import com.github.dropguard.summer.tck.invisible.fixtures.narrow.nested.NestedInterfaceImpl;
import com.github.dropguard.summer.test.SummerTestExtension;
import com.github.dropguard.summer.test.annotation.DualEngine;
import com.github.dropguard.summer.test.annotation.SummerTest;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Behavioral dual-engine contract for nested interface references: a bean implementing a nested
 * interface must build AND resolve on both engines. The AOT assertion is implicit in building — the
 * generated interface-keyed registration must reference {@code NestedHolder.Router} correctly (a
 * broken import of the dotted binary name fails the generated compile).
 */
@SummerTest
public class NestedInterfaceDualEngineTest {

    @RegisterExtension
    static SummerTestExtension ext =
            SummerTestExtension.builder()
                    .beanClasses(NestedHolder.class, NestedInterfaceImpl.class)
                    .build();

    @DualEngine
    void nestedInterfaceBeanBuildsAndResolves(BeanContainer container) {
        assertFalse(
                container.getBeans(NestedHolder.Router.class).isEmpty(),
                "the bean must be registered under its nested interface on both engines");
    }
}
