package com.github.dropguard.summer.tck.invisible.fixtures.conditionalproducer;

import com.github.dropguard.summer.core.annotation.Bean;
import com.github.dropguard.summer.core.annotation.ConditionalOnBean;
import com.github.dropguard.summer.core.annotation.Configuration;

/** The conditioned-out producer of {@link Greeter}: its prerequisite never exists. */
@Configuration
public class ConditionalGreeterConfig {
    @Bean
    @ConditionalOnBean(AbsentMarker.class)
    public Greeter conditionalGreeter() {
        return () -> "from-conditional";
    }
}
