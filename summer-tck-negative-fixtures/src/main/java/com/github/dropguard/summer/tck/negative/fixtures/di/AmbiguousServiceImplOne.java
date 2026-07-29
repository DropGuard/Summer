package com.github.dropguard.summer.tck.negative.fixtures.di;

import com.github.dropguard.summer.core.Component;

@Component
public class AmbiguousServiceImplOne implements AmbiguousService {
    @Override
    public String name() {
        return "one";
    }
}
