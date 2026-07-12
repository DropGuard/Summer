package summer.tck.aop;

import summer.core.BeanContainer;

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
	protected BeanContainer createContext() {
		try {

			return summer.test.TestContainerBuilder.buildAot(null);
		} catch (Exception e) {
			throw new RuntimeException("AOT context not available", e);
		}
	}
}