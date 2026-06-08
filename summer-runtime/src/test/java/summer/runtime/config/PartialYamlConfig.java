package summer.runtime.config;

import summer.core.config.ConfigurationProperties;
import summer.core.config.DefaultValue;

/**
 * Record for testing partial YAML binding — some fields in YAML, rest via
 * {@code @DefaultValue}.
 */
@ConfigurationProperties(prefix = "partial")
public record PartialYamlConfig(String host, @DefaultValue("8080") Integer port, @DefaultValue("false") Boolean ssl) {
}
