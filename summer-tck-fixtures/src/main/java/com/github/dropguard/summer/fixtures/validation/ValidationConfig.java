package com.github.dropguard.summer.fixtures.validation;

import com.github.dropguard.summer.core.annotation.Bean;
import com.github.dropguard.summer.core.annotation.Configuration;

/** Entry point for validation TCK tests. */
@Configuration
public class ValidationConfig {

    @Bean
    public TlsService tlsService(TlsConfig config) {
        return new TlsService(config);
    }
}
