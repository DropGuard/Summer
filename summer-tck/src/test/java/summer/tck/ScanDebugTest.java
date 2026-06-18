package summer.tck;

import org.junit.jupiter.api.Test;
import summer.core.ApplicationContext;
import summer.runtime.RuntimeApplicationContext;

class ScanDebugTest {
    @Test
    void testScan() {
        ApplicationContext ctx = RuntimeApplicationContext.create();
        System.out.println("=== Components ===");
        for (Class<?> c : ctx.getRegisteredTypes()) {
            System.out.println("  " + c.getName() + " isInterface=" + c.isInterface());
        }
    }
}
