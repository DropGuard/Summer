package com.github.dropguard.summer.fixtures.lifecycle;

import com.github.dropguard.summer.core.Component;
import jakarta.annotation.PreDestroy;

/**
 * Declares BOTH teardown mechanisms — {@code @PreDestroy} and {@code AutoCloseable} — so the
 * shutdown order contract (annotation first, convention second) is observable in one bean.
 */
@Component
public class PreDestroyInner implements AutoCloseable {

    private volatile boolean alive = true;

    @PreDestroy
    public void destroy() {
        LifecycleEvents.EVENTS.add("inner.preDestroy");
    }

    @Override
    public void close() {
        alive = false;
        LifecycleEvents.EVENTS.add("inner.close");
    }

    public boolean isAlive() {
        return alive;
    }
}
