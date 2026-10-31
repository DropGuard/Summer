package com.github.dropguard.summer.tck.invisible.fixtures.startup;

import com.github.dropguard.summer.core.Component;

/**
 * Closeable bean built BEFORE a sibling's constructor blows up. Implements AutoCloseable, so it is
 * registered under its concrete class AND that interface key — the identity-deduped abandoned-bean
 * sweep must close it exactly once, not once per type key.
 */
@Component
public class LeakyConnectionPool implements AutoCloseable {

    public static int closeCount;

    @Override
    public void close() {
        closeCount++;
    }
}
