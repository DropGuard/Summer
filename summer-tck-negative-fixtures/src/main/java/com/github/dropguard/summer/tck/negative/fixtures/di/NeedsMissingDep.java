package com.github.dropguard.summer.tck.negative.fixtures.di;

import com.github.dropguard.summer.core.Component;

/**
 * Bean whose constructor requires a dependency that is never registered. The container must fail
 * (NoSuchBeanException / BeanCreationException) rather than silently wiring a null.
 */
@Component
public class NeedsMissingDep {

    private final MissingDep dep;

    public NeedsMissingDep(MissingDep dep) {
        this.dep = dep;
    }

    public MissingDep dep() {
        return dep;
    }
}
