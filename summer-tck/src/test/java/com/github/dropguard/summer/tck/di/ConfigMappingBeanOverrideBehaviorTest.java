package com.github.dropguard.summer.tck.di;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.core.Engine;
import com.github.dropguard.summer.tck.invisible.fixtures.override.OverrideConfig;
import com.github.dropguard.summer.tck.invisible.fixtures.override.OverrideProps;
import com.github.dropguard.summer.test.TestContainer;
import org.junit.jupiter.api.Test;

/**
 * Behavior contract for the @ConfigMapping override semantics: a {@code @Bean} producer for a
 * {@code @ConfigMapping} type is an explicit override — the producer wins over the mapping's own
 * discovery registration, regardless of index iteration order. Before the deterministic dedup this
 * was order-dependent: when the mapping's registration landed after the producer's, both survived
 * and the container failed with a confusing "multiple beans" ambiguity. Runs on both engines.
 */
public class ConfigMappingBeanOverrideBehaviorTest {

    @Test
    void beanProducerOverridesConfigMappingDeterministically() {
        for (Engine engine : Engine.values()) {
            BeanContainer context =
                    TestContainer.builder()
                            .testClass(getClass())
                            .engine(engine)
                            .beans(OverrideProps.class, OverrideConfig.class)
                            .build();
            assertEquals(
                    "from-producer",
                    context.getBean(OverrideProps.class).value(),
                    engine + " invocation");
        }
    }
}
