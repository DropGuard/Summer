package com.github.dropguard.summer.tck.aop;

import com.github.dropguard.summer.tck.invisible.fixtures.aop.AopMarker;
import com.github.dropguard.summer.tck.invisible.fixtures.aop.MarkerInterceptor;
import com.github.dropguard.summer.tck.invisible.fixtures.aop.NoInterfaceBoundBean;
import com.github.dropguard.summer.test.SummerTestExtension;
import com.github.dropguard.summer.test.annotation.DualEngine;
import org.junit.jupiter.api.extension.RegisterExtension;

public class AopNoInterfaceFailFastTest {

    @RegisterExtension
    static SummerTestExtension ext =
            SummerTestExtension.builder()
                    .beanClasses(
                            NoInterfaceBoundBean.class, MarkerInterceptor.class, AopMarker.class)
                    .shouldFail()
                    .build();

    @DualEngine
    void noInterfaceBindingRejected() {}
}
