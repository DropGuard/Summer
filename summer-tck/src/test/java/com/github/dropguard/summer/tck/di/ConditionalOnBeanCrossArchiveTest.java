package com.github.dropguard.summer.tck.di;

import static org.junit.jupiter.api.Assertions.*;

import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.core.annotation.Bean;
import com.github.dropguard.summer.core.annotation.ConditionalOnBean;
import com.github.dropguard.summer.core.annotation.Configuration;
import com.github.dropguard.summer.fixtures.di.conditional.RequiredComponent;
import com.github.dropguard.summer.test.annotation.DualEngine;
import com.github.dropguard.summer.test.annotation.SummerTest;

/**
 * Pins the visibility contract of {@code @ConditionalOnBean}: conditions see the WHOLE container
 * universe — same single model as bean injection. The probe config lives in the test-classes
 * archive ("test"); {@code RequiredComponent} lives in the summer-tck-fixtures jar. Different
 * archives, one container: the condition must match.
 */
@SummerTest
public class ConditionalOnBeanCrossArchiveTest {

    public static class CrossArchiveProbe {}

    @Configuration
    public static class ProbeConfig {

        @Bean
        @ConditionalOnBean(RequiredComponent.class)
        public CrossArchiveProbe crossArchiveProbe() {
            return new CrossArchiveProbe();
        }
    }

    @DualEngine
    void conditionMatchesAcrossArchives(BeanContainer context) {
        assertEquals(
                1,
                context.getBeans(CrossArchiveProbe.class).size(),
                "@ConditionalOnBean must see the whole universe, not just its own archive");
    }
}
