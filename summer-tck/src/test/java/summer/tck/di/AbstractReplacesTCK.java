package summer.tck.di;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import summer.core.ApplicationContext;
import summer.tck.AbstractContextTCK;
import summer.fixtures.di.replaces.OriginalComponent;
import summer.fixtures.di.replaces.ReplacableService;
import summer.fixtures.di.replaces.ServiceBean;

/**
 * TCK test for {@code @Replaces} and {@code @ConditionalOnBean} interaction.
 *
 * <p>
 * Each test creates a fresh context by scanning a package
 * for @Component-annotated classes. Subclasses provide the context factory.
 * </p>
 *
 * <p>
 * Note: This TCK extends {@link AbstractContextTCK} for the primary context and
 * adds a second context factory for conditional replacement testing.
 * </p>
 */
public abstract class AbstractReplacesTCK extends AbstractContextTCK {

	private ApplicationContext conditionalContext;

	/**
	 * Creates a fresh context that includes ReplacesWithConditionComponent but NOT
	 * ReplacementComponent.
	 */
	protected abstract ApplicationContext createConditionalReplacesContext();

	/**
	 * Get the conditional replaces context (lazy initialization).
	 */
	protected ApplicationContext conditionalContext() {
		if (conditionalContext == null) {
			conditionalContext = createConditionalReplacesContext();
		}
		return conditionalContext;
	}

	@AfterEach
	void cleanupConditionalContext() {
		closeQuietly(conditionalContext);
		conditionalContext = null;
	}

	// --- @Replaces basics ---

	@Test
	void testReplacementHappens() {
		ReplacableService service = context().getBean(ReplacableService.class);
		assertNotNull(service);
		assertEquals("replacement", service.serve(), "ReplacementComponent should replace OriginalComponent");
	}

	@Test
	void testOriginalIsRemoved() {
		assertThrows(Exception.class, () -> context().getBean(OriginalComponent.class),
				"OriginalComponent should be removed after replacement");
	}

	// --- @ConditionalOnBean + @Replaces interaction ---

	@Test
	void testConditionalReplacesConditionUnmet() {
		ApplicationContext ctx = conditionalContext();

		// ReplacesWithConditionComponent has
		// @ConditionalOnBean(NonExistentMarker.class)
		// NonExistentMarker is NOT registered as a component, so condition is unmet
		assertThrows(Exception.class,
				() -> ctx.getBean(summer.fixtures.di.replaces.conditional.ReplacesWithConditionComponent.class));

		// OriginalComponent (from conditional package) should survive
		summer.fixtures.di.replaces.conditional.OriginalComponent original = ctx
				.getBean(summer.fixtures.di.replaces.conditional.OriginalComponent.class);
		assertNotNull(original, "OriginalComponent should survive when conditional replacement's condition is unmet");
		assertEquals("original", original.serve());
	}

	// --- @Configuration @Replaces cascade ---

	@Test
	void testConfigurationReplacesCascade() {
		// ReplacementBeanConfig replaces OriginalBeanConfig.
		// The @Bean serviceBean() on OriginalBeanConfig should also be removed.
		// If not, we'd get AmbiguousBeanException (two ServiceBean beans).
		ServiceBean bean = context().getBean(ServiceBean.class);
		assertNotNull(bean);
		assertEquals("replacement", bean.source(), "ReplacementBeanConfig's @Bean should produce the ServiceBean");
	}
}
