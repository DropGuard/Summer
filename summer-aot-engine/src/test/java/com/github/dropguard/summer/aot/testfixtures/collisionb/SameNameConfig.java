package com.github.dropguard.summer.aot.testfixtures.collisionb;

import com.github.dropguard.summer.core.config.ConfigMapping;

/** Same simple name as {@code collisiona.SameNameConfig} — the collision fixture. */
@ConfigMapping(prefix = "collisionb")
public interface SameNameConfig {

    String value();
}
