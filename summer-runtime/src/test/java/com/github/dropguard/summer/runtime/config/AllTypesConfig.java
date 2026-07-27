package com.github.dropguard.summer.runtime.config;

import com.github.dropguard.summer.core.config.ConfigMapping;
import com.github.dropguard.summer.core.config.WithDefault;

/** Config mapping covering all types supported by {@code TypeConverter}. */
@ConfigMapping(prefix = "all-types")
public interface AllTypesConfig {

    @WithDefault("unnamed")
    String name();

    @WithDefault("hello")
    String defaultedString();

    @WithDefault("42")
    Integer intVal();

    @WithDefault("9999999999")
    Long longVal();

    @WithDefault("3.14")
    Double doubleVal();

    @WithDefault("true")
    Boolean boolVal();
}
