package com.github.dropguard.summer.tck.di;

import com.github.dropguard.summer.tck.negative.fixtures.di.MissingDep;
import com.github.dropguard.summer.tck.negative.fixtures.di.NeedsMissingDep;
import com.github.dropguard.summer.test.SummerTestExtension;
import com.github.dropguard.summer.test.annotation.DualEngine;
import org.junit.jupiter.api.extension.RegisterExtension;

public class MissingDependencyBehaviorTest {

    @RegisterExtension
    static SummerTestExtension ext =
            SummerTestExtension.builder()
                    .beanClasses(NeedsMissingDep.class, MissingDep.class)
                    .shouldFail()
                    .build();

    @DualEngine
    void missingDependencyRejected() {}
}
