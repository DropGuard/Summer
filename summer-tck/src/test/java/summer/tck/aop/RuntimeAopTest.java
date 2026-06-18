package summer.tck.aop;

import summer.core.BeanContainer;
import summer.fixtures.aop.GreeterService;
import summer.runtime.RuntimeApplicationContext;

/**
 * Runs the full AOP TCK against the Runtime (reflection + JDK proxy) engine.
 * Expected: ALL tests pass immediately, since RuntimeApplicationContext already
 * applies AOP proxies during bean instantiation.
 */
public class RuntimeAopTest extends AbstractAopTCK {

	@Override
	protected BeanContainer createContext() {
		return RuntimeApplicationContext.create();
	}
}
