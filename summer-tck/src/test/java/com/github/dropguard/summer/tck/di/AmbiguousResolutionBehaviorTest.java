package com.github.dropguard.summer.tck.di;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.tck.negative.fixtures.di.AmbiguousService;
import com.github.dropguard.summer.tck.negative.fixtures.di.AmbiguousServiceImplOne;
import com.github.dropguard.summer.tck.negative.fixtures.di.AmbiguousServiceImplTwo;
import com.github.dropguard.summer.test.SummerTestExtension;
import com.github.dropguard.summer.test.annotation.DualEngine;
import com.github.dropguard.summer.test.annotation.SummerTest;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Dual-engine contract: resolving a type with two {@code @Component} implementations. GAP: should
 * throw {@code AmbiguousBeanException} but silently resolves one impl.
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
    void ambiguousResolutionResolvesSilently() {
        AmbiguousService resolved = container.getBean(AmbiguousService.class);
        assertNotNull(resolved, "GAP: ambiguity is not enforced; one impl is resolved silently");
    }
}
