package com.github.dropguard.summer.tck.invisible.fixtures.override;

import com.github.dropguard.summer.core.annotation.Bean;
import com.github.dropguard.summer.core.annotation.Configuration;

/**
 * A producer returning the concrete implementation (the natural style) instead of the interface —
 * the override contract must hold for this shape too: the synthetic {@code @ConfigMapping} default
 * is dropped and the product wins.
 */
@Configuration
public class OverrideConcreteConfig {

    @Bean
    public OverridePropsImpl overrideProps() {
        return new OverridePropsImpl();
    }
}
