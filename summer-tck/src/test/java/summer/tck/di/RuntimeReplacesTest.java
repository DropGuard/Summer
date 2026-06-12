package summer.tck.di;

import summer.core.ApplicationContext;
import summer.runtime.RuntimeApplicationContext;
import summer.fixtures.di.replaces.ReplacesTestConfig;
import summer.fixtures.di.replaces.conditional.ConditionalReplacesTestConfig;

/**
 * Runtime test for {@code @Replaces} and {@code @ConditionalOnBean} behavior.
 */
public class RuntimeReplacesTest extends AbstractReplacesTCK {

	@Override
	protected ApplicationContext createContext() {
		var ctx = new RuntimeApplicationContext();
		ctx.scan();
		ctx.initializeBeans();
		return ctx;
	}

	@Override
	protected ApplicationContext createConditionalReplacesContext() {
		var ctx = new RuntimeApplicationContext();
		ctx.scan();
		ctx.initializeBeans();
		return ctx;
	}
}
