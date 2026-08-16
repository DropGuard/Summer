package com.github.dropguard.summer.tck.invisible.fixtures.di;

import com.github.dropguard.summer.core.Component;
import jakarta.annotation.PostConstruct;

/** Bean whose {@code @PostConstruct} method declares a parameter — enrichment must reject. */
@Component
public class ParamPostConstructBean {

    @PostConstruct
    public void initialize(String arg) {}
}
