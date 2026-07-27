package com.github.dropguard.summer.fixtures.di.replaces.conditional;

import com.github.dropguard.summer.core.Component;
import com.github.dropguard.summer.core.annotation.ConditionalOnBean;
import com.github.dropguard.summer.core.annotation.Replaces;

@ConditionalOnBean(NonExistentMarker.class)
@Replaces(OriginalComponent.class)
@Component
public class ReplacesWithConditionComponent implements ReplacableService {

    @Override
    public String serve() {
        return "conditional-replacement";
    }
}
