package summer.tck.aop;

import summer.core.ApplicationContext;
import summer.core.aot.GeneratedAotContext;

/**
 * AOT engine test for AOP interception.
 *
 * <p>
 * Runs the same TCK as {@link RuntimeAopTest} but against the AOT-generated
 * context. Both engines must produce identical AOP behavior.
 * </p>
 */
public class AotAopTest extends AbstractAopTCK {

	@Override
	protected ApplicationContext createContext() {
		return new GeneratedAotContext();
	}
}
