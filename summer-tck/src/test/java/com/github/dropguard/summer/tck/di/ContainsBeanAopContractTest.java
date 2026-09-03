package com.github.dropguard.summer.tck.di;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.core.exception.NoSuchBeanException;
import com.github.dropguard.summer.fixtures.aop.Greeter;
import com.github.dropguard.summer.test.annotation.DualEngine;
import com.github.dropguard.summer.test.annotation.SummerTest;

/**
 * TCK: {@link BeanContainer#containsBean} must agree with {@link BeanContainer#getBean} on whether
 * a type is resolvable. Under the one-bean-one-form AOP contract, an AOP-bound bean's
 * concrete-class key holds its proxy; the proxy is not an instance of that class, so neither method
 * should treat the concrete type as a hit. Returning {@code true} from {@code
 * containsBean(ConcreteClass)} while {@code getBean} throws {@code NoSuchBeanException} for the
 * same type was a silent contradiction between the two APIs.
 */
@SummerTest
public class ContainsBeanAopContractTest {

    @DualEngine
    void aopBoundBeanContainsAndGetsByInterface(BeanContainer context) {
        // The interface lookup must agree in both directions.
        assertTrue(
                context.containsBean(Greeter.class), "interface must resolve through containsBean");
        assertTrue(
                context.getBean(Greeter.class) != null || context.containsBean(Greeter.class),
                "containsBean/interface must be consistent with getBean/interface");
    }

    @DualEngine
    void aopBoundBeanConcreteTypeIsNeitherContainsNorGet(BeanContainer context) {
        // The regression: containsBean(ConcreteClass) used to return true while getBean threw.
        assertFalse(
                context.containsBean(com.github.dropguard.summer.fixtures.aop.GreeterService.class),
                "containsBean on an AOP-bound concrete class must return false");
        assertThrows(
                NoSuchBeanException.class,
                () ->
                        context.getBean(
                                com.github.dropguard.summer.fixtures.aop.GreeterService.class),
                "getBean on an AOP-bound concrete class must still fail loudly");
    }

    @DualEngine
    void unboundTypeReturnsFalse(BeanContainer context) {
        // Sanity: a type the universe can't possibly contain returns false without throwing on
        // both engines, and getBean throws NoSuchBeanException for the same type. This is the
        // ordinary miss path, not the AOP edge — present here so a future regression that
        // makes containsBean silently swallow unbound types would surface here too.
        assertFalse(context.containsBean(java.nio.file.Path.class));
        assertThrows(NoSuchBeanException.class, () -> context.getBean(java.nio.file.Path.class));
    }
}
