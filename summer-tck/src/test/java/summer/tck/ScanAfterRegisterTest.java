package summer.tck;

import org.junit.jupiter.api.Test;
import summer.core.BeanContainer;
import summer.runtime.RuntimeApplicationContext;
import summer.core.Engine;

class ScanAfterRegisterTest {
    @Test
    void testScanAfterRegister() {
        BeanContainer ctx = RuntimeApplicationContext.create(summer.core.Engine.RUNTIME);
        System.out.println("=== Components ===");
        for (Class<?> c : ctx.componentTypes()) {
            System.out.println("  " + c.getName());
        }
    }
}