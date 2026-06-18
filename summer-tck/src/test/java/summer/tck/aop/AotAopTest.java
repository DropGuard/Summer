package summer.tck.aop;

import summer.core.ApplicationContext;

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
		try {
			Class<?> aotClass = Class.forName("summer.core.aot.GeneratedAotContext");
			return (ApplicationContext) aotClass.getMethod("create").invoke(null);
		} catch (Exception e) {
			throw new RuntimeException("AOT context not available", e);
		}
	}
}