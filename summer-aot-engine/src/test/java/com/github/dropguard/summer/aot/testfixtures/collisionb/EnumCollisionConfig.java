package com.github.dropguard.summer.aot.testfixtures.collisionb;

import com.github.dropguard.summer.core.config.ConfigMapping;

/** The second config with a {@code Mode} enum of the same simple name (different package). */
@ConfigMapping(prefix = "collisionb")
public interface EnumCollisionConfig {

    enum Mode {
        b
    }

    Mode mode();
}
