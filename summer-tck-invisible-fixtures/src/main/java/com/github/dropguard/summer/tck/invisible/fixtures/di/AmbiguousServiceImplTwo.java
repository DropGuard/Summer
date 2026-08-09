package com.github.dropguard.summer.tck.invisible.fixtures.di;

import com.github.dropguard.summer.core.Component;

@Component
public class AmbiguousServiceImplTwo implements AmbiguousService {
    @Override
    public String name() {
        return "two";
    }
}
