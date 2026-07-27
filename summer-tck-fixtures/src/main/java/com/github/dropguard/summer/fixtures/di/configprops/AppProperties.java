package com.github.dropguard.summer.fixtures.di.configprops;

import com.github.dropguard.summer.core.config.ConfigMapping;

/** Test fixture: Quarkus-style config mapping bound from the {@code app:} YAML section. */
@ConfigMapping(prefix = "app")
public interface AppProperties {

    String name();

    Integer port();

    Boolean verbose();
}
