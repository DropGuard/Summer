package summer.runtime;

import org.junit.jupiter.api.Test;
import summer.core.ApplicationContext;

class ScanDebugTest {
	@Test
	void testScan() {
		ApplicationContext ctx = RuntimeApplicationContext.create();
		System.out.println("Components: " + ctx.getRegisteredTypes().size());
		for (Class<?> c : ctx.getRegisteredTypes()) {
			System.out.println("  " + c.getName() + " isInterface=" + c.isInterface());
		}
	}
}
