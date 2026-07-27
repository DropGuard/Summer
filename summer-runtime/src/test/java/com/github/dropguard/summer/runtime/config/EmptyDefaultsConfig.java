package com.github.dropguard.summer.runtime.config;

import com.github.dropguard.summer.core.config.ConfigMapping;
import com.github.dropguard.summer.core.config.WithDefault;

/**
 * Config mapping where all keys have {@code @WithDefault}. Tests that defaults are correctly
 * applied for each type when YAML is absent.
 */
@ConfigMapping(prefix = "empty-defaults")
public interface EmptyDefaultsConfig {

    @WithDefault("")
    String emptyStr();

    @WithDefault("0")
    Integer zeroInt();

    @WithDefault("0")
    Long zeroLong();

    @WithDefault("0.0")
    Double zeroDouble();

    @WithDefault("false")
    Boolean falseBool();
}
