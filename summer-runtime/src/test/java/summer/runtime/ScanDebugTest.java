package summer.runtime;

import org.junit.jupiter.api.Test;

class ScanDebugTest {
	@Test
	void testScan() {
		RuntimeApplicationContext ctx = new RuntimeApplicationContext();
		ctx.scan();
		ctx.initializeBeans();
		System.out.println("Components: " + ctx.getRegisteredTypes().size());
		for (Class<?> c : ctx.getRegisteredTypes()) {
			System.out.println("  " + c.getName() + " isInterface=" + c.isInterface());
		}
	}
}
