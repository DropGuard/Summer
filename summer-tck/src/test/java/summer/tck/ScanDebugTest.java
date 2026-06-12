package summer.tck;

import org.junit.jupiter.api.Test;
import summer.runtime.RuntimeApplicationContext;

class ScanDebugTest {
    @Test
    void testScan() {
        RuntimeApplicationContext ctx = new RuntimeApplicationContext();
        ctx.scan();
        ctx.initializeBeans();
        System.out.println("=== Components ===");
        for (Class<?> c : ctx.getRegisteredTypes()) {
            System.out.println("  " + c.getName() + " isInterface=" + c.isInterface());
        }
    }
}
