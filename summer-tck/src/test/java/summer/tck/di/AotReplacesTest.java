package summer.tck.di;

import summer.core.BeanContainer;

/**
 * AOT engine test for {@code @Replaces} behavior.
 *
 * <p>
 * Runs the same TCK as {@link RuntimeReplacesTest} but against the
 * AOT-generated context. Both engines must produce identical @Replaces
 * behavior.
 * </p>
 */
public class AotReplacesTest extends AbstractReplacesTCK {

	@Override
	protected BeanContainer createContext() {
		try {
			return summer.test.TestContainerBuilder.buildAot(null);
		} catch (Exception e) {
			throw new RuntimeException("AOT context not available", e);
		}
	}
}
