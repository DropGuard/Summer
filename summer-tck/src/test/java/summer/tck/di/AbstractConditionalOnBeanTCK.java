package summer.tck.di;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import summer.core.ApplicationContext;
import summer.tck.AbstractContextTCK;
import summer.tck.di.conditional.*;

/**
 * TCK test for @ConditionalOnBean behavior.
 *
 * <p>Tests whether the DI container correctly registers or skips beans based on
 * @ConditionalOnBean conditions.</p>
 */
public abstract class AbstractConditionalOnBeanTCK extends AbstractContextTCK {

	@Test
	void testContextStartsSuccessfully() {
		assertNotNull(context(), "ApplicationContext should not be null");
	}

	@Test
	void testConditionalOnConcreteClass() {
		ApplicationContext ctx = context();
		// RequiredComponent exists, so ConditionalOnComponent should be registered
		RequiredComponent required = ctx.getBean(RequiredComponent.class);
		assertNotNull(required, "RequiredComponent should be registered");

		ConditionalOnComponent conditional = ctx.getBean(ConditionalOnComponent.class);
		assertNotNull(conditional, "ConditionalOnComponent should be registered when RequiredComponent exists");
	}

	@Test
	void testConditionalOnMissingComponent() {
		ApplicationContext ctx = context();
		// MissingComponent does not exist, so ConditionalOnMissingComponent should NOT
		// be registered
		assertThrows(Exception.class, () -> ctx.getBean(ConditionalOnMissingComponent.class),
				"ConditionalOnMissingComponent should NOT be registered when MissingComponent does not exist");
	}

	@Test
	void testConditionalOnInterface() {
		ApplicationContext ctx = context();
		// RequiredInterface exists (via RequiredInterfaceImpl), so
		// ConditionalOnInterface should be registered
		RequiredInterface required = ctx.getBean(RequiredInterface.class);
		assertNotNull(required, "RequiredInterface should be registered");

		ConditionalOnInterface conditional = ctx.getBean(ConditionalOnInterface.class);
		assertNotNull(conditional, "ConditionalOnInterface should be registered when RequiredInterface exists");
	}
}
