package com.github.dropguard.summer.fixtures.di;

import com.github.dropguard.summer.core.Component;
import com.github.dropguard.summer.core.FrozenState;
import jakarta.annotation.PostConstruct;

/**
 * Fixture for the {@code @PostConstruct} lifecycle boundary (the CDI config-phase-end callback):
 * initializes in the callback and seals itself, so a mutator throws after container assembly — the
 * post-assembly immutability pattern the framework's {@code @PostConstruct} support exists for.
 */
@Component
public class PostConstructFixture {

    private final FrozenState state = new FrozenState();
    private int initializedValue = -1;

    @PostConstruct
    public void initialize() {
        this.initializedValue = 42;
        this.state.freeze();
    }

    public int initializedValue() {
        return initializedValue;
    }

    /** Mutator guarded by the frozen state — must throw once assembly is done. */
    public void mutate() {
        state.ensureMutable("mutate");
        initializedValue++;
    }

    public boolean isFrozen() {
        return state.isFrozen();
    }
}
