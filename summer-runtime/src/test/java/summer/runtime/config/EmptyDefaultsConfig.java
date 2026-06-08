package summer.runtime.config;

import summer.core.config.ConfigurationProperties;
import summer.core.config.DefaultValue;

/**
 * Record where all fields have {@code @DefaultValue}. Tests that defaults are
 * correctly applied for each type when YAML is absent.
 */
@ConfigurationProperties(prefix = "empty-defaults")
public record EmptyDefaultsConfig(@DefaultValue("") String emptyStr, @DefaultValue("0") Integer zeroInt,
		@DefaultValue("0") Long zeroLong, @DefaultValue("0.0") Double zeroDouble,
		@DefaultValue("false") Boolean falseBool) {
}
