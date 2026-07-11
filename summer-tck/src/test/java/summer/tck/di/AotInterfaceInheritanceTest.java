package summer.tck.di;

import summer.core.BeanContainer;

/**
 * AOT engine test for interface inheritance dependency resolution.
 *
 * <p>
 * Runs the same TCK as {@link RuntimeInterfaceInheritanceTest} but against the
 * AOT-generated context. Both engines must produce identical interface
 * inheritance resolution behavior.
 * </p>
 */
public class AotInterfaceInheritanceTest extends AbstractInterfaceInheritanceTCK {

	@Override
	protected BeanContainer createContext() {
		try {
			return summer.test.TestContainerBuilder.buildAot(null);
		} catch (Exception e) {
			throw new RuntimeException("AOT context not available", e);
		}
	}
}
