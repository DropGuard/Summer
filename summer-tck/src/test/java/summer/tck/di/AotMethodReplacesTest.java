package summer.tck.di;

import summer.core.BeanContainer;

/**
 * AOT engine test for method-level {@code @Replaces} behavior.
 *
 * <p>
 * Runs the same TCK as {@link RuntimeMethodReplacesTest} but against the
 * AOT-generated context. Both engines must produce identical method-level
 * replacement behavior.
 * </p>
 */
public class AotMethodReplacesTest extends AbstractMethodReplacesTCK {

	@Override
	protected BeanContainer createContext() {
		try {
			return summer.test.TestContainerBuilder.buildAot(null);
		} catch (Exception e) {
			throw new RuntimeException("AOT context not available", e);
		}
	}
}
