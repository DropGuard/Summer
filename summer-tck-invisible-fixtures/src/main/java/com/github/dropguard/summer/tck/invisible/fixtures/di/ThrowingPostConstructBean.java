package com.github.dropguard.summer.tck.invisible.fixtures.di;

import com.github.dropguard.summer.core.Component;
import jakarta.annotation.PostConstruct;

/**
 * Bean whose {@code @PostConstruct} throws at runtime — both engines must surface the failure as a
 * {@code BeanCreationException} naming this bean (not a raw exception, and not a generic AOT
 * compilation error).
 */
@Component
public class ThrowingPostConstructBean {

    @PostConstruct
    public void initialize() {
        throw new IllegalStateException("post-construct exploded");
    }
}
