package summer.tck.di;

import summer.core.ApplicationContext;
import summer.runtime.RuntimeApplicationContext;
import summer.tck.di.conditional.RequiredComponent;

/**
 * Runtime test for @ConditionalOnBean behavior.
 */
public class RuntimeConditionalOnBeanTest extends AbstractConditionalOnBeanTCK {

	@Override
	protected ApplicationContext createContext() {
		return RuntimeApplicationContext.create(RequiredComponent.class);
	}
}


