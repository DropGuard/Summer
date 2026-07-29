package com.github.dropguard.summer.tck.negative.fixtures.di;

import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.core.Component;

/**
 * Bean that requests the container itself for injection. The container must reject this
 * (UnsupportedInjectionException) — injecting the container into a bean would create a circular
 * bootstrap reference.
 */
@Component
public class SelfInjectingBean {

    private final BeanContainer container;

    public SelfInjectingBean(BeanContainer container) {
        this.container = container;
    }

    public BeanContainer container() {
        return container;
    }
}
