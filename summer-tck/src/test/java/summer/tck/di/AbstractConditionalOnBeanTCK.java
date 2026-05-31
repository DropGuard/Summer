package summer.tck.di;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import summer.core.ApplicationContext;
import summer.tck.di.conditional.*;

/**
 * TCK test for @ConditionalOnBean behavior.
 *
 * <p>
 * Tests whether the DI container correctly registers or skips beans based on
 * @ConditionalOnBean conditions.
 */
public abstract class AbstractConditionalOnBeanTCK {

	protected ApplicationContext context;

	protected abstract ApplicationContext createAndInitializeContext();

	protected ApplicationContext getContext() {
		if (context == null) {
			context = createAndInitializeContext();
		}
		return context;
	}

	@AfterEach
	void tearDown() {
		if (context != null) {
			context.destroy();
			context = null;
		}
	}

	@Test
	void testContextStartsSuccessfully() {
		assertNotNull(getContext(), "ApplicationContext should not be null");
	}

	@Test
	void testConditionalOnConcreteClass() {
		ApplicationContext ctx = getContext();
		// RequiredComponent exists, so ConditionalOnComponent should be registered
		RequiredComponent required = ctx.getBean(RequiredComponent.class);
		assertNotNull(required, "RequiredComponent should be registered");

		ConditionalOnComponent conditional = ctx.getBean(ConditionalOnComponent.class);
		assertNotNull(conditional, "ConditionalOnComponent should be registered when RequiredComponent exists");
	}

	@Test
	void testConditionalOnMissingComponent() {
		ApplicationContext ctx = getContext();
		// MissingComponent does not exist, so ConditionalOnMissingComponent should NOT be registered
		assertThrows(Exception.class, () -> ctx.getBean(ConditionalOnMissingComponent.class),
				"ConditionalOnMissingComponent should NOT be registered when MissingComponent does not exist");
	}

	@Test
	void testConditionalOnInterface() {
		ApplicationContext ctx = getContext();
		// RequiredInterface exists (via RequiredInterfaceImpl), so ConditionalOnInterface should be registered
		RequiredInterface required = ctx.getBean(RequiredInterface.class);
		assertNotNull(required, "RequiredInterface should be registered");

		ConditionalOnInterface conditional = ctx.getBean(ConditionalOnInterface.class);
		assertNotNull(conditional,
				"ConditionalOnInterface should be registered when RequiredInterface exists");
	}
}
