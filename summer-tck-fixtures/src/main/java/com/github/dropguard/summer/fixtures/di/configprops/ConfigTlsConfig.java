package com.github.dropguard.summer.fixtures.di.configprops;

import com.github.dropguard.summer.core.annotation.Bean;
import com.github.dropguard.summer.core.annotation.Configuration;

/**
 * Test fixture: configuration that depends on auto-bound TlsProperties. Entry point for
 * testing @ConfigMapping with non-bean constructor params.
 */
@Configuration
public class ConfigTlsConfig {

    @Bean
    public ConfigTlsService tlsService(TlsProperties properties) {
        return new ConfigTlsService(properties);
    }
}
