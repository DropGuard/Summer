package summer.tck;

import org.junit.jupiter.api.Test;
import summer.runtime.RuntimeApplicationContext;

class ScanAfterRegisterTest {
    @Test
    void testScanAfterRegister() {
        RuntimeApplicationContext ctx = new RuntimeApplicationContext();
        ctx.scan();
        ctx.initializeBeans();
        System.out.println("=== Components ===");
        for (Class<?> c : ctx.getRegisteredTypes()) {
            System.out.println("  " + c.getName());
        }
    }
}
