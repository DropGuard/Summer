package com.github.dropguard.summer.tck.di;

import com.github.dropguard.summer.tck.invisible.fixtures.di.SelfInjectingBean;
import com.github.dropguard.summer.test.SummerTestExtension;
import com.github.dropguard.summer.test.annotation.DualEngine;
import org.junit.jupiter.api.extension.RegisterExtension;

public class SelfInjectionBehaviorTest {

    @RegisterExtension
    static SummerTestExtension ext =
            SummerTestExtension.builder().beanClasses(SelfInjectingBean.class).shouldFail().build();

    @DualEngine
    void selfInjectionRejected() {}
}
