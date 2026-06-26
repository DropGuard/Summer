package summer.tck.di;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import summer.fixtures.di.replaces.OriginalComponent;
import summer.fixtures.di.replaces.ReplacableService;
import summer.fixtures.di.replaces.ServiceBean;
import summer.tck.AbstractContextTCK;

/**
 * TCK test for {@code @Replaces} and {@code @ConditionalOnBean} interaction.
 *
 * <p>
 * Uses a single context with full classpath scan to test both scenarios:
 * <ul>
 * <li>{@code @Replaces} without condition - replacement happens</li>
 * <li>{@code @Replaces} with {@code @ConditionalOnBean} - replacement skipped
 * when condition unmet</li>
 * </ul>
 */
public abstract class AbstractReplacesTCK extends AbstractContextTCK {

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
		// ReplacesWithConditionComponent has
		// @ConditionalOnBean(NonExistentMarker.class)
		// NonExistentMarker is NOT registered as a component, so condition is unmet
		assertThrows(Exception.class,
				() -> context().getBean(summer.fixtures.di.replaces.conditional.ReplacesWithConditionComponent.class));

		// OriginalComponent (from conditional package) should survive
		summer.fixtures.di.replaces.conditional.OriginalComponent original = context()
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
