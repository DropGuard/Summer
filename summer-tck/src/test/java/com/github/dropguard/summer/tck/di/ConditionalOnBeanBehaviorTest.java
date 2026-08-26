package com.github.dropguard.summer.tck.di;

import static org.junit.jupiter.api.Assertions.*;

import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.fixtures.di.conditional.*;
import com.github.dropguard.summer.test.annotation.DualEngine;
import com.github.dropguard.summer.test.annotation.SummerTest;

@SummerTest
public class ConditionalOnBeanBehaviorTest {

    @DualEngine
    void testContextStartsSuccessfully(BeanContainer context) {
        assertNotNull(context, "BeanContainer should not be null");
    }

    @DualEngine
    void testConditionalOnConcreteClass(BeanContainer context) {
        RequiredComponent required = context.getBean(RequiredComponent.class);
        assertNotNull(required, "RequiredComponent should be registered");

        ConditionalOnComponent conditional = context.getBean(ConditionalOnComponent.class);
        assertNotNull(
                conditional,
                "ConditionalOnComponent should be registered when RequiredComponent exists");
    }

    @DualEngine
    void testConditionalOnMissingComponent(BeanContainer context) {
        assertThrows(
                Exception.class,
                () -> context.getBean(ConditionalOnMissingComponent.class),
                "ConditionalOnMissingComponent should NOT be registered when MissingComponent does"
                        + " not exist");
    }

    @DualEngine
    void testConditionalOnInterface(BeanContainer context) {
        RequiredInterface required = context.getBean(RequiredInterface.class);
        assertNotNull(required, "RequiredInterface should be registered");

        ConditionalOnInterface conditional = context.getBean(ConditionalOnInterface.class);
        assertNotNull(
                conditional,
                "ConditionalOnInterface should be registered when RequiredInterface exists");
    }

    @DualEngine
    void testAndSemanticsClassAndMethodConditions(BeanContainer context) {
        // AND semantics (Quarkus/Spring parity): a @Bean product's class-level and method-level
        // conditions are BOTH checked — the method-level one must NOT let a failing class-level
        // prerequisite through (the regression a single-slot evaluator would cause).
        assertThrows(
                Exception.class,
                () -> context.getBean(MethodOnlySatisfiedProduct.class),
                "class-level prerequisite is missing — the bean must be excluded despite the"
                        + " method-level condition being satisfied");

        assertNotNull(
                context.getBean(BothConditionalProduct.class),
                "both prerequisites exist — the bean must be registered");
    }
}
