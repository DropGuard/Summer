package com.github.dropguard.summer.fixtures.di.root;

import com.github.dropguard.summer.core.config.ConfigMapping;

/**
 * Test fixture: {@code @ConfigMapping} with empty prefix — binds the entire YAML root; the {@code
 * root:} nested section is its own mapping interface.
 */
@ConfigMapping(prefix = "")
public interface RootProperties {

    /** Nested mapping matching the {@code root:} YAML section. */
    @ConfigMapping(prefix = "root")
    interface Root {

        String host();

        Integer port();
    }

    Root root();
}
