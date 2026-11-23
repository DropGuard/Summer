package com.github.dropguard.summer.tck.di;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.core.Engine;
import com.github.dropguard.summer.tck.invisible.fixtures.conditionalproducer.ConditionalGatedPropsConfig;
import com.github.dropguard.summer.tck.invisible.fixtures.conditionalproducer.ConditionalGreeterConfig;
import com.github.dropguard.summer.tck.invisible.fixtures.conditionalproducer.GatedProps;
import com.github.dropguard.summer.tck.invisible.fixtures.conditionalproducer.Greeter;
import com.github.dropguard.summer.tck.invisible.fixtures.conditionalproducer.PlainGreeterConfig;
import com.github.dropguard.summer.test.TestContainer;
import org.junit.jupiter.api.Test;

/**
 * End-to-end contract for {@code @ConditionalOnBean} on {@code @Bean} producers, from real
 * annotations through discovery and condition evaluation to the built container, on both engines.
 * The evaluator's unit tests use hand-built definitions; these lock that discovery actually
 * produces what the evaluator keys on.
 */
public class ConditionalProducerBehaviorTest {

    @Test
    void sameTypeProducersAreEvaluatedIndependently() {
        for (Engine engine : Engine.values()) {
            BeanContainer context =
                    TestContainer.builder()
                            .testClass(getClass())
                            .engine(engine)
                            .beans(ConditionalGreeterConfig.class, PlainGreeterConfig.class)
                            .build();
            assertEquals(
                    "from-plain",
                    context.getBean(Greeter.class).greeting(),
                    engine
                            + " invocation: the unconditional producer survives, the"
                            + " conditioned-out one does not");
        }
    }

    @Test
    void conditionedOutProducerDoesNotOverrideConfigMapping() {
        for (Engine engine : Engine.values()) {
            BeanContainer context =
                    TestContainer.builder()
                            .testClass(getClass())
                            .engine(engine)
                            .beans(GatedProps.class, ConditionalGatedPropsConfig.class)
                            .build();
            assertEquals(
                    "from-mapping",
                    context.getBean(GatedProps.class).value(),
                    engine
                            + " invocation: the @ConfigMapping registration stays when its only"
                            + " producer is conditioned out");
        }
    }
}
