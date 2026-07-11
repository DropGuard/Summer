package summer.tck.di;

import summer.core.BeanContainer;

/**
 * AOT engine test for {@code @ConditionalOnBean} behavior.
 *
 * <p>
 * Runs the same TCK as {@link RuntimeConditionalOnBeanTest} but against the
 * AOT-generated context. Both engines must produce identical conditional
 * assembly behavior.
 * </p>
 */
public class AotConditionalOnBeanTest extends AbstractConditionalOnBeanTCK {

	@Override
	protected BeanContainer createContext() {
		try {
			return summer.test.TestContainerBuilder.buildAot(null);
		} catch (Exception e) {
			throw new RuntimeException("AOT context not available", e);
		}
	}
}
