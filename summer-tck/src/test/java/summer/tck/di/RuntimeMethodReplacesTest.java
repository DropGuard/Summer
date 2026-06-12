package summer.tck.di;

import summer.core.ApplicationContext;
import summer.runtime.RuntimeApplicationContext;

/**
 * Runtime test for method-level {@code @Replaces} behavior.
 */
public class RuntimeMethodReplacesTest extends AbstractMethodReplacesTCK {

	@Override
	protected ApplicationContext createContext() {
		var ctx = new RuntimeApplicationContext();
		ctx.scan();
		ctx.initializeBeans();
		return ctx;
	}
}
