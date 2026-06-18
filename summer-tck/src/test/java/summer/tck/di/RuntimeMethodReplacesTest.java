package summer.tck.di;

import summer.core.BeanContainer;
import summer.runtime.RuntimeApplicationContext;
import summer.core.Engine;

/**
 * Runtime test for method-level {@code @Replaces} behavior.
 */
public class RuntimeMethodReplacesTest extends AbstractMethodReplacesTCK {

	@Override
	protected BeanContainer createContext() {
		return RuntimeApplicationContext.create(summer.core.Engine.RUNTIME);
	}
}