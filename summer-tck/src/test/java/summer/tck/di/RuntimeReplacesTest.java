package summer.tck.di;

import summer.core.ApplicationContext;
import summer.runtime.RuntimeApplicationContext;
import summer.tck.di.replaces.ReplacesTestConfig;
import summer.tck.di.replaces.conditional.ConditionalReplacesTestConfig;

/**
 * Runtime test for {@code @Replaces} and {@code @ConditionalOnBean} behavior.
 */
public class RuntimeReplacesTest extends AbstractReplacesTCK {

	@Override
	protected ApplicationContext createContext() {
		return RuntimeApplicationContext.create(ReplacesTestConfig.class);
	}

	@Override
	protected ApplicationContext createConditionalReplacesContext() {
		return RuntimeApplicationContext.create(ConditionalReplacesTestConfig.class);
	}
}
