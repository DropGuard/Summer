package summer.tck.di;

import summer.core.ApplicationContext;
import summer.runtime.RuntimeApplicationContext;

/**
 * Runtime test for {@code @Replaces} and {@code @ConditionalOnBean} behavior.
 */
public class RuntimeReplacesTest extends AbstractReplacesTCK {

	@Override
	protected ApplicationContext createContext(Class<?>... components) {
		RuntimeApplicationContext ctx = new RuntimeApplicationContext();
		for (Class<?> c : components) {
			ctx.registerComponent(c);
		}
		ctx.initializeBeans();
		return ctx;
	}
}
