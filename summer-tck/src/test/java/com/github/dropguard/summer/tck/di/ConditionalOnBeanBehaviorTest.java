package com.github.dropguard.summer.tck.di;

import static org.junit.jupiter.api.Assertions.*;

import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.fixtures.di.conditional.*;
import com.github.dropguard.summer.test.annotation.DualEngine;
import com.github.dropguard.summer.test.annotation.SummerTest;

@SummerTest
public class ConditionalOnBeanBehaviorTest {

	private final BeanContainer context;

	public ConditionalOnBeanBehaviorTest(BeanContainer context) {
		this.context = context;
	}

	@DualEngine
	void testContextStartsSuccessfully() {
		assertNotNull(context, "BeanContainer should not be null");
	}

	@DualEngine
	void testConditionalOnConcreteClass() {
		RequiredComponent required = context.getBean(RequiredComponent.class);
		assertNotNull(required, "RequiredComponent should be registered");

		ConditionalOnComponent conditional = context.getBean(ConditionalOnComponent.class);
		assertNotNull(conditional, "ConditionalOnComponent should be registered when RequiredComponent exists");
	}

	@DualEngine
	void testConditionalOnMissingComponent() {
		assertThrows(Exception.class, () -> context.getBean(ConditionalOnMissingComponent.class),
				"ConditionalOnMissingComponent should NOT be registered when MissingComponent does not exist");
	}

	@DualEngine
	void testConditionalOnInterface() {
		RequiredInterface required = context.getBean(RequiredInterface.class);
		assertNotNull(required, "RequiredInterface should be registered");

		ConditionalOnInterface conditional = context.getBean(ConditionalOnInterface.class);
		assertNotNull(conditional, "ConditionalOnInterface should be registered when RequiredInterface exists");
	}
}
