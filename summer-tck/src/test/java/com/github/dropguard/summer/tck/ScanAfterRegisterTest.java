package com.github.dropguard.summer.tck;

import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.test.TestContainer;
import org.junit.jupiter.api.Test;

class ScanAfterRegisterTest {
    @Test
    void testScanAfterRegister() {
        BeanContainer ctx = TestContainer.build();
        System.out.println("=== Components ===");
        for (Class<?> c : ctx.componentTypes()) {
            System.out.println("  " + c.getName());
        }
    }
}
