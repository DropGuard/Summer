package com.github.dropguard.summer.fixtures.di;

import com.github.dropguard.summer.core.annotation.Bean;
import com.github.dropguard.summer.core.annotation.Configuration;

/** Original configuration that provides a bean for method-level @Replaces tests. */
@Configuration
public class MethodReplacesConfig {

    @Bean
    public MethodReplacesBean methodReplacesBean() {
        return new MethodReplacesBean("original");
    }
}
