package com.github.dropguard.summer.fixtures.di.configprops;

import com.github.dropguard.summer.core.config.ConfigMapping;
import com.github.dropguard.summer.core.config.WithDefault;

/**
 * Fixture for the dual-engine enum-convergence contract: mixed-case enum constants (a legitimate
 * Java style the AOT's old {@code Enum.valueOf(raw.toUpperCase())} could not bind) must resolve
 * from lowercase YAML and from {@code @WithDefault} on both engines — the Jackson {@code
 * ACCEPT_CASE_INSENSITIVE_ENUMS} contract the runtime proxy uses, now mirrored in the generated
 * code.
 */
@ConfigMapping(prefix = "env")
public interface EnvironmentConfig {

    enum Mode {
        production,
        staging
    }

    Mode mode();

    @WithDefault("staging")
    Mode fallbackMode();
}
