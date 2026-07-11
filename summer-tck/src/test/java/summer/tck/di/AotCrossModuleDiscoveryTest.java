package summer.tck.di;

import summer.core.BeanContainer;

/**
 * AOT engine test for cross-module bean discovery.
 *
 * <p>
 * Runs the same TCK as {@link RuntimeCrossModuleDiscoveryTest} but against the
 * AOT-generated context. Both engines must discover beans from external modules
 * identically.
 * </p>
 */
public class AotCrossModuleDiscoveryTest extends AbstractCrossModuleDiscoveryTCK {

	@Override
	protected BeanContainer createContext() {
		try {
			return summer.test.TestContainerBuilder.buildAot(null);
		} catch (Exception e) {
			throw new RuntimeException("AOT context not available", e);
		}
	}
}
