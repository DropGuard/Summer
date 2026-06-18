package summer.tck.di;

import summer.core.BeanContainer;
import summer.runtime.RuntimeApplicationContext;

/**
 * Runtime test for method-level {@code @Replaces} behavior.
 */
public class RuntimeMethodReplacesTest extends AbstractMethodReplacesTCK {

	@Override
	protected BeanContainer createContext() {
		return RuntimeApplicationContext.createRuntime();
	}
}