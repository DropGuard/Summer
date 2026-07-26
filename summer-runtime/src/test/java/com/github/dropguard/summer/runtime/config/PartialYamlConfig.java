package com.github.dropguard.summer.runtime.config;

import com.github.dropguard.summer.core.config.ConfigurationProperties;
import com.github.dropguard.summer.core.config.DefaultValue;

/**
 * Record for testing partial YAML binding — some fields in YAML, rest via
 * {@code @DefaultValue}.
 */
@ConfigurationProperties(prefix = "partial")
public record PartialYamlConfig(String host, @DefaultValue("8080") Integer port, @DefaultValue("false") Boolean ssl) {
}
