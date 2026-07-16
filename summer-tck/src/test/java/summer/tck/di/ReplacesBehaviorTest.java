package summer.tck.di;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import summer.core.BeanContainer;
import summer.fixtures.di.replaces.OriginalComponent;
import summer.fixtures.di.replaces.ReplacableService;
import summer.fixtures.di.replaces.ServiceBean;
import summer.test.annotation.SummerTest;

@SummerTest(modules = "summer-tck-fixtures")
public class ReplacesBehaviorTest {

	private final BeanContainer context;

	public ReplacesBehaviorTest(BeanContainer context) {
		this.context = context;
	}

	@Test
	void testReplacementHappens() {
		ReplacableService service = context.getBean(ReplacableService.class);
		assertNotNull(service);
		assertEquals("replacement", service.serve(), "ReplacementComponent should replace OriginalComponent");
	}

	@Test
	void testOriginalIsRemoved() {
		assertThrows(Exception.class, () -> context.getBean(OriginalComponent.class),
				"OriginalComponent should be removed after replacement");
	}

	@Test
	void testConditionalReplacesConditionUnmet() {
		assertThrows(Exception.class,
				() -> context.getBean(summer.fixtures.di.replaces.conditional.ReplacesWithConditionComponent.class));

		summer.fixtures.di.replaces.conditional.OriginalComponent original = context
				.getBean(summer.fixtures.di.replaces.conditional.OriginalComponent.class);
		assertNotNull(original, "OriginalComponent should survive when conditional replacement's condition is unmet");
		assertEquals("original", original.serve());
	}

	@Test
	void testConfigurationReplacesCascade() {
		ServiceBean bean = context.getBean(ServiceBean.class);
		assertNotNull(bean);
		assertEquals("replacement", bean.source(), "ReplacementBeanConfig's @Bean should produce the ServiceBean");
	}
}
