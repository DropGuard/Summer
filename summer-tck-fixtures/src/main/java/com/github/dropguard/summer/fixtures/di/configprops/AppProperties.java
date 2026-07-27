package com.github.dropguard.summer.fixtures.di.configprops;

import com.github.dropguard.summer.core.config.ConfigurationProperties;

/** Test fixture: configuration properties bound from application.yml. */
@ConfigurationProperties(prefix = "app")
public record AppProperties(String name, Integer port, Boolean verbose) {}
