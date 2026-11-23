package com.github.dropguard.summer.tck.invisible.fixtures.combo;

import com.github.dropguard.summer.core.Component;
import com.github.dropguard.summer.core.annotation.ConditionalOnBean;

/** A replacement target that is itself conditioned out. */
@Component
@ConditionalOnBean(ComboAbsent.class)
public class ComboLegacyGreeter implements ComboGreeter {
    @Override
    public String greet() {
        return "legacy";
    }
}
