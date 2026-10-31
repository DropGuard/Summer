package com.github.dropguard.summer.fixtures.lifecycle;

import com.github.dropguard.summer.core.Component;
import jakarta.annotation.PreDestroy;

/**
 * Depends on {@link PreDestroyInner} (so it is created after it and torn down before it) and
 * declares ONLY {@code @PreDestroy} — no {@code AutoCloseable} — pinning that the annotation alone
 * is enough, and that a dependent's destroy runs while its dependency is still alive.
 */
@Component
public class PreDestroyOuter {

    private final PreDestroyInner inner;

    public PreDestroyOuter(PreDestroyInner inner) {
        this.inner = inner;
    }

    @PreDestroy
    public void destroy() {
        LifecycleEvents.EVENTS.add("outer.preDestroy:innerAlive=" + inner.isAlive());
    }
}
