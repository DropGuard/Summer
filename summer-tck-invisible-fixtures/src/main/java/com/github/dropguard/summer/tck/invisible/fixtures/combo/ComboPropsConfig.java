package com.github.dropguard.summer.tck.invisible.fixtures.combo;

import com.github.dropguard.summer.core.annotation.Bean;
import com.github.dropguard.summer.core.annotation.Configuration;

/** Overrides {@link ComboProps} — unless this configuration is itself replaced. */
@Configuration
public class ComboPropsConfig {
    @Bean
    public ComboProps comboProps() {
        return () -> "from-producer";
    }
}
