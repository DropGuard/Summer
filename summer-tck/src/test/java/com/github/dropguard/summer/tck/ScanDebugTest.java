package com.github.dropguard.summer.tck;

import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.runtime.RuntimeBeanContainerBuilder;
import org.junit.jupiter.api.Test;

class ScanDebugTest {
    @Test
    void testScan() {
        BeanContainer ctx = RuntimeBeanContainerBuilder.build();
        System.out.println("=== Components ===");
        for (Class<?> c : ctx.componentTypes()) {
            System.out.println("  " + c.getName() + " isInterface=" + c.isInterface());
        }
    }
}
