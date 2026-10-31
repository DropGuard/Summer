package com.github.dropguard.summer.core;

import java.util.Objects;

/**
 * Guard helper for the post-assembly immutability pattern: a bean stays mutable while the container
 * assembles it, then transitions to read-only. {@code volatile} gives safe publication: {@code
 * freeze()} runs on the assembly thread, concurrent readers see it immediately.
 *
 * <p>Three legitimate shapes, by who owns the timing:
 *
 * <ul>
 *   <li><b>Self-freeze</b> — {@code freeze()} on the last line of the bean's own constructor
 *       (constructor injection makes that the "fully wired" point); the container is not involved.
 *   <li><b>Container-timed, enforced by this guard</b> — the bean implements {@link Sealable}, its
 *       {@code seal()} calls {@code freeze()}, and the container supplies the timing at the end of
 *       assembly (the {@code JdbcTemplate} shape: assembly-time writers are OTHER beans).
 *   <li><b>Container-timed only</b> — {@code Sealable} without this guard: the bean does its own
 *       bookkeeping in {@code seal()} and needs no write enforcement.
 * </ul>
 *
 * <p>The guard is a pre-check with better error messages, not the enforcement itself — pair it with
 * structural immutability (swap the backing collection to an unmodifiable snapshot at freeze) so a
 * mutator that forgets {@code ensureMutable()} still fails on the data structure.
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
