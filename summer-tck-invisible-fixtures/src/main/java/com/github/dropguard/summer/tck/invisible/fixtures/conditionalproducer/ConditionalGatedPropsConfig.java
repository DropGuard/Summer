package com.github.dropguard.summer.tck.invisible.fixtures.conditionalproducer;

import com.github.dropguard.summer.core.annotation.Bean;
import com.github.dropguard.summer.core.annotation.ConditionalOnBean;
import com.github.dropguard.summer.core.annotation.Configuration;

/**
 * A conditioned-out producer for {@link GatedProps}. Only a producer that survives its conditions
 * may replace the mapping's own registration; this one never does, so the mapping must stay.
 */
@Configuration
public class ConditionalGatedPropsConfig {
    @Bean
    @ConditionalOnBean(AbsentMarker.class)
    public GatedProps gatedProps() {
        return () -> "from-producer";
    }
}
