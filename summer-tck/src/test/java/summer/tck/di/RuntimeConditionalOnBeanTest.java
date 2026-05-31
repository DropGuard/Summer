package summer.tck.di;

import summer.core.ApplicationContext;
import summer.scanner.runtime.RuntimeDiEngine;
import summer.tck.di.conditional.RequiredComponent;

/**
 * Runtime test for @ConditionalOnBean behavior.
 */
public class RuntimeConditionalOnBeanTest extends AbstractConditionalOnBeanTCK {

	@Override
	protected ApplicationContext createAndInitializeContext() {
		return new RuntimeDiEngine().create(RequiredComponent.class);
	}
}
