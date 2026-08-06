package com.github.dropguard.summer.fixtures.di.conditional;

import com.github.dropguard.summer.core.annotation.Bean;
import com.github.dropguard.summer.core.annotation.ConditionalOnBean;
import com.github.dropguard.summer.core.annotation.Configuration;

@Configuration
public class MethodOnlySatisfiedConfig {

    @Bean
    @ConditionalOnBean(MethodLevelDependency.class)
    public MethodOnlySatisfiedProduct product() {
        return new MethodOnlySatisfiedProduct();
    }
}
