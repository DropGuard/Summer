package com.github.dropguard.summer.runtime.config;

import com.github.dropguard.summer.core.config.ConfigMapping;
import com.github.dropguard.summer.core.config.WithDefault;

/**
 * Config mapping for testing partial YAML binding — some keys in YAML, rest via
 * {@code @WithDefault}.
 */
@ConfigMapping(prefix = "partial")
public interface PartialYamlConfig {

    String host();

    @WithDefault("8080")
    Integer port();

    @WithDefault("false")
    Boolean ssl();
}
