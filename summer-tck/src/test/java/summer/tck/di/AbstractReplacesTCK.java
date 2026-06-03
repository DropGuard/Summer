package summer.tck.di;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import summer.core.ApplicationContext;
import summer.tck.di.conditional.*;
import summer.tck.di.replaces.*;

/**
 * TCK test for {@code @Replaces} and {@code @ConditionalOnBean} interaction.
 *
 * <p>
 * Each test creates a fresh context with specific components registered.
 * Subclasses provide the context factory.
 * </p>
 */
public abstract class AbstractReplacesTCK {

	protected ApplicationContext context;

	/**
	 * Creates a fresh context with the given component classes registered and
	 * initialized.
	 */
	protected abstract ApplicationContext createContext(Class<?>... components);

	@AfterEach
	void tearDown() {
		if (context != null) {
			context.destroy();
			context = null;
		}
	}

	// --- @Replaces basics ---

	@Test
	void testReplacementHappens() {
		context = createContext(OriginalComponent.class, ReplacementComponent.class);

		ReplacableService service = context.getBean(ReplacableService.class);
		assertNotNull(service);
		assertEquals("replacement", service.serve(),
				"ReplacementComponent should replace OriginalComponent");
	}

	@Test
	void testOriginalIsRemoved() {
		context = createContext(OriginalComponent.class, ReplacementComponent.class);

		assertThrows(Exception.class, () -> context.getBean(OriginalComponent.class),
				"OriginalComponent should be removed after replacement");
	}

	// --- @ConditionalOnBean ---

	@Test
	void testConditionalOnBeanPositive() {
		context = createContext(RequiredComponent.class, ConditionalOnComponent.class);

		assertNotNull(context.getBean(RequiredComponent.class));
		assertNotNull(context.getBean(ConditionalOnComponent.class),
				"ConditionalOnComponent should be registered when RequiredComponent exists");
	}

	@Test
	void testConditionalOnBeanNegative() {
		context = createContext(ConditionalOnMissingComponent.class);
		// MissingComponent NOT registered

		assertThrows(Exception.class, () -> context.getBean(ConditionalOnMissingComponent.class),
				"ConditionalOnMissingComponent should NOT be registered when MissingComponent does not exist");
	}

	// --- @ConditionalOnBean + @Replaces interaction ---

	@Test
	void testConditionalReplacesConditionUnmet() {
		context = createContext(OriginalComponent.class, ReplacesWithConditionComponent.class);
		// NonExistentMarker NOT registered → condition unmet

		// ReplacesWithConditionComponent should NOT be registered (condition unmet)
		assertThrows(Exception.class, () -> context.getBean(ReplacesWithConditionComponent.class));

		// OriginalComponent should survive (replacement never fired)
		OriginalComponent original = context.getBean(OriginalComponent.class);
		assertNotNull(original, "OriginalComponent should survive when conditional replacement's condition is unmet");
		assertEquals("original", original.serve());
	}
}
