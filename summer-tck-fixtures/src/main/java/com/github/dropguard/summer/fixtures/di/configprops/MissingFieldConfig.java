package com.github.dropguard.summer.fixtures.di.configprops;

import com.github.dropguard.summer.core.config.ConfigMapping;

/**
 * Fixture for the missing-field contract: a {@code @ConfigMapping} method with no
 * {@code @WithDefault} whose key is absent from the YAML section must throw {@code
 * com.github.dropguard.summer.core.exception.MissingFieldException} on access — on both engines
 * (the runtime proxy and the AOT {@code $$ConfigImpl} both fail lazily at the getter).
 */
@ConfigMapping(prefix = "app")
public interface MissingFieldConfig {

    String notPresent();
}
