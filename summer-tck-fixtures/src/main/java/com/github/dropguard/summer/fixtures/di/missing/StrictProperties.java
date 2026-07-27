package com.github.dropguard.summer.fixtures.di.missing;

import com.github.dropguard.summer.core.config.ConfigMapping;

/**
 * Test fixture: {@code @ConfigMapping} where ALL fields are required (no {@code @WithDefault}).
 * Used to verify that missing fields throw MissingFieldException.
 */
@ConfigMapping(prefix = "strict")
public interface StrictProperties {

    String apiKey();
}
