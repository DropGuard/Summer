package com.github.dropguard.summer.aot.testfixtures.collisiona;

import com.github.dropguard.summer.core.config.ConfigMapping;
import com.github.dropguard.summer.core.config.WithDefault;

/** A config interface using the top-level {@code Mode} enum from the same package. */
@ConfigMapping(prefix = "collisiona")
public interface EnumCollisionConfig {

    enum Mode {
        a
    }

    Mode mode();

    @WithDefault("b")
    com.github.dropguard.summer.aot.testfixtures.collisionb.EnumCollisionConfig.Mode other();
}
