package com.github.dropguard.summer.tck.invisible.fixtures.combo;

import com.github.dropguard.summer.core.Component;

/** An unconditional replacement target. */
@Component
public class ComboBaseGreeter implements ComboGreeter {
    @Override
    public String greet() {
        return "base";
    }
}
