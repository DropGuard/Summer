package com.github.dropguard.summer.tck.invisible.fixtures.combo;

import com.github.dropguard.summer.core.config.ConfigMapping;
import com.github.dropguard.summer.core.config.WithDefault;

@ConfigMapping(prefix = "combo")
public interface ComboProps {
    @WithDefault("from-mapping")
    String value();
}
