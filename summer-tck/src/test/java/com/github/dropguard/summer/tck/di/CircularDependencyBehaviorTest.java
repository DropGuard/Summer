package com.github.dropguard.summer.tck.di;

import com.github.dropguard.summer.tck.negative.fixtures.di.CycleNodeA;
import com.github.dropguard.summer.tck.negative.fixtures.di.CycleNodeB;
import com.github.dropguard.summer.test.SummerTestExtension;
import com.github.dropguard.summer.test.annotation.DualEngine;
import org.junit.jupiter.api.extension.RegisterExtension;

public class CircularDependencyBehaviorTest {

    @RegisterExtension
    static SummerTestExtension ext =
            SummerTestExtension.builder()
                    .beanClasses(CycleNodeA.class, CycleNodeB.class)
                    .shouldFail()
                    .build();

    @DualEngine
    void cycleDetected() {}
}
