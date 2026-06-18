package summer.tck;

import org.junit.jupiter.api.Test;
import summer.core.BeanContainer;
import summer.runtime.RuntimeApplicationContext;

class ScanDebugTest {
    @Test
    void testScan() {
        BeanContainer ctx = RuntimeApplicationContext.create();
        System.out.println("=== Components ===");
        for (Class<?> c : ctx.componentTypes()) {
            System.out.println("  " + c.getName() + " isInterface=" + c.isInterface());
        }
    }
}