package com.github.dropguard.summer.runtime;

import com.github.dropguard.summer.core.config.ConfigurationProperties;
import com.github.dropguard.summer.core.config.DefaultValue;

@ConfigurationProperties(prefix = "test")
public record DefaultValueTestRecord(@DefaultValue("false") Boolean enabled, @DefaultValue("") String name) {
}
