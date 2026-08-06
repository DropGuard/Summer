package com.github.dropguard.summer.aot.testfixtures.collisiona;

import com.github.dropguard.summer.core.config.ConfigMapping;

/** Same simple name as {@code collisionb.SameNameConfig} — the collision fixture. */
@ConfigMapping(prefix = "collisiona")
public interface SameNameConfig {

    String value();
}
