package com.github.dropguard.summer.tck.di;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.tck.invisible.fixtures.narrow.dup.A.Thing;
import com.github.dropguard.summer.test.SummerTestExtension;
import com.github.dropguard.summer.test.annotation.DualEngine;
import com.github.dropguard.summer.test.annotation.SummerTest;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Behavioral dual-engine contract for codegen variable-name dedup: two {@code @Component}s with the
 * same simple name in different packages are legitimate user code, and the container must build AND
 * resolve both on BOTH engines. On the AOT engine the assertion is implicit in building: the
 * generated wire declares one variable per bean, so without dedup the generated code would fail to
 * compile and the container build would error out.
 */
@SummerTest
public class DupSimpleNameDualEngineTest {

    @RegisterExtension
    static SummerTestExtension ext =
            SummerTestExtension.builder()
                    .beanClasses(
                            Thing.class,
                            com.github.dropguard.summer.tck.invisible.fixtures.narrow.dup.B.Thing
                                    .class)
                    .build();

    @DualEngine
    void sameSimpleNameBeansBuildAndResolve(BeanContainer container) {
        assertNotNull(
                container.getBean(Thing.class), "dup.A.Thing must be registered and resolvable");
        assertNotNull(
                container.getBean(
                        com.github.dropguard.summer.tck.invisible.fixtures.narrow.dup.B.Thing
                                .class),
                "dup.B.Thing must be registered and resolvable");
    }
}
