package com.github.dropguard.summer.fixtures.di.replaces;

import com.github.dropguard.summer.core.annotation.Bean;
import com.github.dropguard.summer.core.annotation.Configuration;

@Configuration
public class OriginalBeanConfig {

    @Bean
    public ServiceBean serviceBean() {
        return new ServiceBean("original");
    }
}
