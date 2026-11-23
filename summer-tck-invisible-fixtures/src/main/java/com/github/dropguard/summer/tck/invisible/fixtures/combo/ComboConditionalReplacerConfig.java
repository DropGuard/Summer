package com.github.dropguard.summer.tck.invisible.fixtures.combo;

import com.github.dropguard.summer.core.annotation.Bean;
import com.github.dropguard.summer.core.annotation.ConditionalOnBean;
import com.github.dropguard.summer.core.annotation.Configuration;
import com.github.dropguard.summer.core.annotation.Replaces;

/** A method-level replacer whose own condition fails: it must not replace anything. */
@Configuration
public class ComboConditionalReplacerConfig {
    @Bean
    @Replaces(ComboService.class)
    @ConditionalOnBean(ComboAbsent.class)
    public ComboService overrideService() {
        return () -> "override";
    }
}
