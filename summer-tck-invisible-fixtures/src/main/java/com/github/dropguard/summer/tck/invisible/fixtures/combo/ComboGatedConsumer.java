package com.github.dropguard.summer.tck.invisible.fixtures.combo;

import com.github.dropguard.summer.core.Component;

/** Hard-depends on {@link FeatureGated}: fails to assemble when the gated bean is withdrawn. */
@Component
public class ComboGatedConsumer {
    private final FeatureGated gated;

    public ComboGatedConsumer(FeatureGated gated) {
        this.gated = gated;
    }

    public FeatureGated gated() {
        return gated;
    }
}
