package com.github.dropguard.summer.tck.invisible.fixtures.di;

import com.github.dropguard.summer.core.Component;
import jakarta.annotation.PostConstruct;

/**
 * Bean with a non-public {@code @PostConstruct} method — enrichment must reject it: the AOT engine
 * emits a direct call into the generated context, so a non-public method would silently break AOT
 * compilation (deliberately stricter than CDI, which allows them).
 */
@Component
public class NonPublicPostConstructBean {

    @PostConstruct
    private void initialize() {}
}
