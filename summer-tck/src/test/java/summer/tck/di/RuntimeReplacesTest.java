package summer.tck.di;

import summer.core.BeanContainer;
import summer.runtime.RuntimeApplicationContext;
import summer.fixtures.di.replaces.ReplacesTestConfig;
import summer.fixtures.di.replaces.conditional.ConditionalReplacesTestConfig;

/**
 * Runtime test for {@code @Replaces} and {@code @ConditionalOnBean} behavior.
 */
public class RuntimeReplacesTest extends AbstractReplacesTCK {

	@Override
	protected BeanContainer createContext() {
		return RuntimeApplicationContext.createRuntime();
	}

	@Override
	protected BeanContainer createConditionalReplacesContext() {
		return RuntimeApplicationContext.createRuntime();
	}
}