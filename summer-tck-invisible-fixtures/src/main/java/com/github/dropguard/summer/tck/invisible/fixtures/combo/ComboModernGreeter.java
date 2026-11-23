package com.github.dropguard.summer.tck.invisible.fixtures.combo;

import com.github.dropguard.summer.core.Component;
import com.github.dropguard.summer.core.annotation.Replaces;

/** Replaces a target that conditions have already removed. */
@Component
@Replaces(ComboLegacyGreeter.class)
public class ComboModernGreeter implements ComboGreeter {
    @Override
    public String greet() {
        return "modern";
    }
}
