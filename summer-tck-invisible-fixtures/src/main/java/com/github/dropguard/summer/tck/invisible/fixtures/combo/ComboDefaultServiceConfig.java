package com.github.dropguard.summer.tck.invisible.fixtures.combo;

import com.github.dropguard.summer.core.annotation.Bean;
import com.github.dropguard.summer.core.annotation.Configuration;

@Configuration
public class ComboDefaultServiceConfig {
    @Bean
    public ComboService defaultService() {
        return () -> "default";
    }
}
