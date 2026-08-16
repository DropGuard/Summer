package com.github.dropguard.summer.core;

import java.util.Objects;

/**
 * Guard helper for the post-assembly immutability pattern: a bean stays mutable while the container
 * assembles it, then transitions to read-only — either in a {@code @PostConstruct} method (the CDI
 * config-phase-end boundary) or, for beans filled by other beans during assembly, when the last
 * assembly-time writer calls {@link #freeze()}.
 *
 * <p>Typical use: keep a {@code private final FrozenState} field, call {@link #ensureMutable()} (or
 * {@link #ensureMutable(String)}) as the first statement of every mutator, and flip the state in
 * the bean's own {@code @PostConstruct}. {@code volatile} gives safe publication: {@code freeze()}
 * runs on the assembly thread, concurrent readers see it immediately.
 *
 * <p>{@link #freeze()} is idempotent — a bean may transition more than once (e.g. an AOP proxy and
 * the raw instance are both present in the container).
 */
public final class FrozenState {

    /** Volatile: freeze() runs on the assembly thread, runtime reads see it (safe publication). */
    private volatile boolean frozen;

    /** Returns whether this state has been frozen. */
    public boolean isFrozen() {
        return frozen;
    }

    /** Marks this state frozen. Idempotent. */
    public void freeze() {
        frozen = true;
    }

    /** Guards a mutator — the first statement of every write operation. */
    public void ensureMutable() {
        if (frozen) {
            throw new IllegalStateException(
                    "This bean is frozen: writes are no longer allowed after container assembly");
        }
    }

    /** {@link #ensureMutable()} with an operation name in the message. */
    public void ensureMutable(String operation) {
        if (frozen) {
            throw new IllegalStateException(
                    "Cannot "
                            + Objects.requireNonNull(operation, "operation")
                            + " on a frozen bean (frozen after container assembly)");
        }
    }
}
