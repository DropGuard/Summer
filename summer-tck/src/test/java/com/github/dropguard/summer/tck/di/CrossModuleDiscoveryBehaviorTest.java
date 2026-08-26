package com.github.dropguard.summer.tck.di;

import static org.junit.jupiter.api.Assertions.*;

import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.test.annotation.DualEngine;
import com.github.dropguard.summer.test.annotation.SummerTest;

@SummerTest
public class CrossModuleDiscoveryBehaviorTest {

    @DualEngine
    void discoversBeanFromExternalModule(BeanContainer context) {
        assertDoesNotThrow(
                () -> context.getBean(com.github.dropguard.summer.fixtures.dummy.ServiceA.class),
                "Bean from external module (summer-tck-fixtures) should be discoverable");
    }

    @DualEngine
    void resolvesCrossModuleDependencyChain(BeanContainer context) {
        com.github.dropguard.summer.fixtures.dummy.ServiceA a =
                context.getBean(com.github.dropguard.summer.fixtures.dummy.ServiceA.class);
        assertNotNull(a.getServiceB(), "ServiceA should have ServiceB injected");
        assertNotNull(a.getServiceB().getServiceC(), "ServiceB should have ServiceC injected");
        assertEquals("Hello from C", a.getServiceB().getServiceC().getMessage());
    }

    @DualEngine
    void crossModuleBeansAreSingletons(BeanContainer context) {
        com.github.dropguard.summer.fixtures.dummy.ServiceA a1 =
                context.getBean(com.github.dropguard.summer.fixtures.dummy.ServiceA.class);
        com.github.dropguard.summer.fixtures.dummy.ServiceA a2 =
                context.getBean(com.github.dropguard.summer.fixtures.dummy.ServiceA.class);
        assertSame(a1, a2, "Cross-module beans should be singletons");
    }
}
