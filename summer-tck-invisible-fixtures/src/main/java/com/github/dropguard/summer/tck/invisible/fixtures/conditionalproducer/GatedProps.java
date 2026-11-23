package com.github.dropguard.summer.tck.invisible.fixtures.conditionalproducer;

import com.github.dropguard.summer.core.config.ConfigMapping;
import com.github.dropguard.summer.core.config.WithDefault;

/** Config mapping whose only would-be override is a producer that is conditioned out. */
@ConfigMapping(prefix = "gated")
public interface GatedProps {
    @WithDefault("from-mapping")
    String value();
}
