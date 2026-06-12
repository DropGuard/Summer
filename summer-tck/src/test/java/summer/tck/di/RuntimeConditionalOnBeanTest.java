package summer.tck.di;

import summer.core.ApplicationContext;
import summer.runtime.RuntimeApplicationContext;
import summer.fixtures.di.conditional.RequiredComponent;

/**
 * Runtime test for @ConditionalOnBean behavior.
 */
public class RuntimeConditionalOnBeanTest extends AbstractConditionalOnBeanTCK {

	@Override
	protected ApplicationContext createContext() {
		var ctx = new RuntimeApplicationContext();
		ctx.scan();
		ctx.initializeBeans();
		return ctx;
	}
}
