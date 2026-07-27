package com.github.dropguard.summer.fixtures.di;

import com.github.dropguard.summer.core.annotation.Bean;
import com.github.dropguard.summer.core.annotation.ConditionalOnBean;
import com.github.dropguard.summer.core.annotation.Configuration;

/**
 * Configuration that conditionally registers {@link ConditionalBean} based on the presence of
 * {@link TestMarker}.
 */
@Configuration
public class ConditionalTestConfig {

    @Bean
    public TestMarker testMarker() {
        return new TestMarker();
    }

    @Bean
    @ConditionalOnBean(TestMarker.class)
    public ConditionalBean conditionalBean() {
        return new ConditionalBean();
    }
}
