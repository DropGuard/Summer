package com.github.dropguard.summer.tck.di;

import static org.junit.jupiter.api.Assertions.*;

import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.fixtures.dummy.ServiceA;
import com.github.dropguard.summer.fixtures.dummy.ServiceB;
import com.github.dropguard.summer.fixtures.dummy.ServiceC;
import com.github.dropguard.summer.test.annotation.DualEngine;
import com.github.dropguard.summer.test.annotation.SummerTest;

/**
 * Verifies core DI behaviour via {@link SummerTest}. Each {@code @Test} uses the Runtime engine
 * (dev mode default).
 */
@SummerTest
public class DependencyInjectionBehaviorTest {

    @DualEngine
    void testContextStartsSuccessfully(BeanContainer context) {
        assertNotNull(context, "BeanContainer should not be null");
    }

    @DualEngine
    void testSingletonUniqueness(BeanContainer context) {
        ServiceC c1 = context.getBean(ServiceC.class);
        ServiceC c2 = context.getBean(ServiceC.class);
        assertNotNull(c1);
        assertSame(c1, c2, "Multiple calls to getBean should return the same singleton instance");
    }

    @DualEngine
    void testDependencyResolution(BeanContainer context) {
        ServiceA a = context.getBean(ServiceA.class);
        ServiceB b = context.getBean(ServiceB.class);
        ServiceC c = context.getBean(ServiceC.class);

        assertNotNull(a);
        assertNotNull(b);
        assertNotNull(c);

        assertSame(b, a.getServiceB(), "ServiceA should be injected with the singleton ServiceB");
        assertSame(c, b.getServiceC(), "ServiceB should be injected with the singleton ServiceC");
    }
}
