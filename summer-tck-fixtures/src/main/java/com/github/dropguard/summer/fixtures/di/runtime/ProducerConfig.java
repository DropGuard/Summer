package com.github.dropguard.summer.fixtures.di.runtime;

import com.github.dropguard.summer.core.annotation.Bean;
import com.github.dropguard.summer.core.annotation.Configuration;

@Configuration
public class ProducerConfig {
    @Bean
    public ProducedBean producedBean() {
        return new ProducedBean("produced-value");
    }
}
