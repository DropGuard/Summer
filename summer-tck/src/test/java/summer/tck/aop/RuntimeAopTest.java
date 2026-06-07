package summer.tck.aop;

import summer.core.ApplicationContext;
import summer.runtime.RuntimeApplicationContext;
import summer.tck.aop.dummy.GreeterService;

/**
 * Runs the full AOP TCK against the Runtime (reflection + JDK proxy) engine.
 * Expected: ALL tests pass immediately, since RuntimeApplicationContext already
 * applies AOP proxies during bean instantiation.
 */
public class RuntimeAopTest extends AbstractAopTCK {

	@Override
	protected ApplicationContext createContext() {
		return RuntimeApplicationContext.create(GreeterService.class);
	}
}
