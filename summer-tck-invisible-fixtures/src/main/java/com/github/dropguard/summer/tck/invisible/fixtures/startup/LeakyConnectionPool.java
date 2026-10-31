package com.github.dropguard.summer.tck.invisible.fixtures.startup;

import com.github.dropguard.summer.core.Component;
import jakarta.annotation.PreDestroy;
import java.util.List;

/**
 * Closeable bean built BEFORE a sibling's constructor blows up. Implements AutoCloseable, so it is
 * registered under its concrete class AND that interface key — the identity-deduped abandoned-bean
 * sweep must tear it down exactly once, not once per type key — and with the full per-bean rule:
 * {@code @PreDestroy} first, then {@code close()}.
 */
@Component
public class LeakyConnectionPool implements AutoCloseable {

    public static int closeCount;
    public static final List<String> EVENTS = new java.util.concurrent.CopyOnWriteArrayList<>();

    @PreDestroy
    public void destroy() {
        EVENTS.add("preDestroy");
    }

    @Override
    public void close() {
        closeCount++;
        EVENTS.add("close");
    }
}
