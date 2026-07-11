package summer.tck.di;

import summer.core.BeanContainer;

/**
 * AOT engine test for generic interface dependency resolution.
 *
 * <p>
 * Runs the same TCK as {@link RuntimeGenericInterfaceTest} but against the
 * AOT-generated context. Both engines must produce identical generic interface
 * resolution behavior.
 * </p>
 */
public class AotGenericInterfaceTest extends AbstractGenericInterfaceTCK {

	@Override
	protected BeanContainer createContext() {
		try {
			return summer.test.TestContainerBuilder.buildAot(null);
		} catch (Exception e) {
			throw new RuntimeException("AOT context not available", e);
		}
	}
}
