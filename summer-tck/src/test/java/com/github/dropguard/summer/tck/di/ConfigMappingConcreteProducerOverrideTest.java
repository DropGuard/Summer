package com.github.dropguard.summer.tck.di;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.core.Engine;
import com.github.dropguard.summer.tck.invisible.fixtures.override.OverrideConcreteConfig;
import com.github.dropguard.summer.tck.invisible.fixtures.override.OverrideProps;
import com.github.dropguard.summer.test.TestContainer;
import org.junit.jupiter.api.Test;

/**
 * The {@code @ConfigMapping} override contract for a producer returning the concrete
 * implementation: the user's declared bean wins over the synthetic YAML-bound default even when the
 * {@code @Bean} method's return type is the implementation class, not the mapping interface.
 */
public class ConfigMappingConcreteProducerOverrideTest {

    @Test
    void concreteReturnProducerOverridesTheSyntheticDefault() throws Exception {
        for (Engine engine : Engine.values()) {
            BeanContainer context =
                    TestContainer.builder()
                            .testClass(getClass())
                            .engine(engine)
                            .beans(OverrideProps.class, OverrideConcreteConfig.class)
                            .build();
            assertEquals(
                    "from-concrete-producer",
                    context.getBean(OverrideProps.class).value(),
                    engine
                            + " invocation: the concrete-return producer must win over the"
                            + " synthetic");
        }
    }
}
