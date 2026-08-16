package com.github.dropguard.summer.tck.di;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.core.exception.AmbiguousBeanException;
import com.github.dropguard.summer.tck.invisible.fixtures.di.AmbiguousServiceImplOne;
import com.github.dropguard.summer.tck.invisible.fixtures.di.AmbiguousServiceImplTwo;
import com.github.dropguard.summer.test.SummerTestExtension;
import com.github.dropguard.summer.test.annotation.DualEngine;
import com.github.dropguard.summer.test.annotation.SummerTest;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Behavioral: resolving a type with two {@code @Component} implementations of one interface is
 * ambiguous and must fail loudly ({@link AmbiguousBeanException}) on both engines — never silently
 * resolve one. (The multi-impl interface key is no longer registered, so {@code getBean} scans both
 * impls by assignability and reports the ambiguity.)
 */
@SummerTest
public class AmbiguousResolutionBehaviorTest {

    @RegisterExtension
    static SummerTestExtension ext =
            SummerTestExtension.builder()
                    .beanClasses(AmbiguousServiceImplOne.class, AmbiguousServiceImplTwo.class)
                    .build();

    private final BeanContainer container;

    public AmbiguousResolutionBehaviorTest(BeanContainer container) {
        this.container = container;
    }

    @DualEngine
    void ambiguousResolutionFailsFastOnBothEngines() {
        assertThrows(
                AmbiguousBeanException.class,
                () ->
                        container.getBean(
                                com.github.dropguard.summer.tck.invisible.fixtures.di
                                        .AmbiguousService.class),
                "two @Component impls of one interface must be rejected as ambiguous, not"
                        + " silently resolved");
    }
}
