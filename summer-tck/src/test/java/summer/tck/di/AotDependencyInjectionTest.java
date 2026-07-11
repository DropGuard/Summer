package summer.tck.di;

import summer.core.BeanContainer;

/**
 * AOT engine test for core dependency injection.
 *
 * <p>
 * Runs the same TCK as {@link RuntimeDiTest} but against the AOT-generated
 * context. Both engines must produce identical DI behavior.
 * </p>
 */
public class AotDependencyInjectionTest extends AbstractDependencyInjectionTCK {

	@Override
	protected BeanContainer createContext() {
		try {
			return summer.test.TestContainerBuilder.buildAot(null);
		} catch (Exception e) {
			throw new RuntimeException("AOT context not available", e);
		}
	}
}
