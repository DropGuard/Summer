package summer.tck.di;

import summer.core.BeanContainer;
import summer.runtime.RuntimeApplicationContext;
import summer.fixtures.di.replaces.ReplacesTestConfig;
import summer.fixtures.di.replaces.conditional.ConditionalReplacesTestConfig;
import summer.core.Engine;

/**
 * Runtime test for {@code @Replaces} and {@code @ConditionalOnBean} behavior.
 */
public class RuntimeReplacesTest extends AbstractReplacesTCK {

	@Override
	protected BeanContainer createContext() {
		return RuntimeApplicationContext.create(summer.core.Engine.RUNTIME);
	}

	@Override
	protected BeanContainer createConditionalReplacesContext() {
		return RuntimeApplicationContext.create(summer.core.Engine.RUNTIME);
	}
}