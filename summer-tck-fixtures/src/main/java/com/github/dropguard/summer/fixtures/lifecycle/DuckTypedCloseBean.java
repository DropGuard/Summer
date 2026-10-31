package com.github.dropguard.summer.fixtures.lifecycle;

import com.github.dropguard.summer.core.Component;

/**
 * Declares a public no-arg {@code close()} WITHOUT implementing {@code AutoCloseable} — the
 * duck-typed cleanup pattern. The container must warn at discovery (the method will never run at
 * shutdown) but still build and never call the method itself.
 */
@Component
public class DuckTypedCloseBean {

    private boolean closed;

    public void close() {
        closed = true;
    }

    public boolean isClosed() {
        return closed;
    }
}
