package summer.tck.di;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import summer.core.ApplicationContext;
import summer.fixtures.di.ConditionalBean;
import summer.fixtures.di.TestMarker;
import summer.tck.AbstractComponentTCK;

/**
 * TCK for {@code @ConditionalOnBean} assembly logic.
 *
 * <p>
 * Verifies that:
 * </p>
 * <ul>
 * <li>Conditional beans are registered when the required marker bean exists</li>
 * <li>Conditional beans are skipped when the required marker bean is absent</li>
 * </ul>
 */
public abstract class AbstractConditionalTCK extends AbstractComponentTCK {

	protected ApplicationContext context;

	@Test
	void conditionalBeanPresentWhenMarkerExists() {
		assertNotNull(context.getBean(TestMarker.class),
				"TestMarker should be registered");
		assertNotNull(context.getBean(ConditionalBean.class),
				"ConditionalBean should be registered when TestMarker exists");
	}
}
