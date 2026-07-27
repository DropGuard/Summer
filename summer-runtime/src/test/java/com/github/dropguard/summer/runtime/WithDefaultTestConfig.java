package com.github.dropguard.summer.runtime;

import com.github.dropguard.summer.core.config.ConfigMapping;
import com.github.dropguard.summer.core.config.WithDefault;

@ConfigMapping(prefix = "test")
public interface WithDefaultTestConfig {

    @WithDefault("false")
    Boolean enabled();

    @WithDefault("")
    String name();
}
