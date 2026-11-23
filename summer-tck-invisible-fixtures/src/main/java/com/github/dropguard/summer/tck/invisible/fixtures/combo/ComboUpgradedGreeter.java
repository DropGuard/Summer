package com.github.dropguard.summer.tck.invisible.fixtures.combo;

import com.github.dropguard.summer.core.Component;
import com.github.dropguard.summer.core.annotation.Replaces;

/** Replaces a target that stands: in production it holds {@link ComboBaseGreeter}'s position. */
@Component
@Replaces(ComboBaseGreeter.class)
public class ComboUpgradedGreeter implements ComboGreeter {
    @Override
    public String greet() {
        return "upgraded";
    }
}
