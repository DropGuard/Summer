package summer.tck.di;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import summer.core.BeanContainer;
import summer.fixtures.di.conditional.*;
import summer.test.annotation.DualEngineTest;

@DualEngineTest
public class ConditionalOnBeanBehaviorTest {

	private final BeanContainer context;

	public ConditionalOnBeanBehaviorTest(BeanContainer context) {
		this.context = context;
	}

	@Test
	void testContextStartsSuccessfully() {
		assertNotNull(context, "BeanContainer should not be null");
	}

	@Test
	void testConditionalOnConcreteClass() {
		RequiredComponent required = context.getBean(RequiredComponent.class);
		assertNotNull(required, "RequiredComponent should be registered");

		ConditionalOnComponent conditional = context.getBean(ConditionalOnComponent.class);
		assertNotNull(conditional, "ConditionalOnComponent should be registered when RequiredComponent exists");
	}

	@Test
	void testConditionalOnMissingComponent() {
		assertThrows(Exception.class, () -> context.getBean(ConditionalOnMissingComponent.class),
				"ConditionalOnMissingComponent should NOT be registered when MissingComponent does not exist");
	}

	@Test
	void testConditionalOnInterface() {
		RequiredInterface required = context.getBean(RequiredInterface.class);
		assertNotNull(required, "RequiredInterface should be registered");

		ConditionalOnInterface conditional = context.getBean(ConditionalOnInterface.class);
		assertNotNull(conditional, "ConditionalOnInterface should be registered when RequiredInterface exists");
	}
}
