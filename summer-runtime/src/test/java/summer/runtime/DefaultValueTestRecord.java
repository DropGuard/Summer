package summer.runtime;

import summer.core.config.ConfigurationProperties;
import summer.core.config.DefaultValue;

@ConfigurationProperties(prefix = "test")
public record DefaultValueTestRecord(@DefaultValue("false") Boolean enabled, @DefaultValue("") String name) {
}
