package summer.tck.di;

import summer.core.BeanContainer;
import summer.runtime.RuntimeApplicationContext;
import summer.fixtures.di.conditional.RequiredComponent;

/**
 * Runtime test for @ConditionalOnBean behavior.
 */
public class RuntimeConditionalOnBeanTest extends AbstractConditionalOnBeanTCK {

	@Override
	protected BeanContainer createContext() {
		return RuntimeApplicationContext.create();
	}
}
