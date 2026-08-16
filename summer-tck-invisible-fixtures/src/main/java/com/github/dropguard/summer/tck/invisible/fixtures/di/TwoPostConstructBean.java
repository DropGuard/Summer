package com.github.dropguard.summer.tck.invisible.fixtures.di;

import com.github.dropguard.summer.core.Component;
import jakarta.annotation.PostConstruct;

/** Bean with two {@code @PostConstruct} methods — enrichment must reject the build. */
@Component
public class TwoPostConstructBean {

    @PostConstruct
    public void first() {}

    @PostConstruct
    public void second() {}
}
