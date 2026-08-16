package com.github.dropguard.summer.tck.invisible.fixtures.di;

import com.github.dropguard.summer.core.Component;
import jakarta.annotation.PostConstruct;

/** Bean with a {@code static} {@code @PostConstruct} method — enrichment must reject the build. */
@Component
public class StaticPostConstructBean {

    @PostConstruct
    public static void initialize() {}
}
