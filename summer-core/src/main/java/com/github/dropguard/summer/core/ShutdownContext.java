package com.github.dropguard.summer.core;

import java.util.ArrayDeque;
import java.util.Deque;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Independent coordinator for application teardown. Input drivers (HTTP / gRPC servers, ...)
 * register a shutdown {@link Runnable} at startup; the container runs every task in reverse
 * registration order on {@link #runAll()}, before the remaining {@link AutoCloseable} beans are
 * closed.
 *
 * <p>This is the single convergence point for "how do I stop myself" — each registrant encapsulates
 * its own staging (stop accepting, drain in-flight, release resources) inside the task, so the
 * coordinator stays transport-agnostic and knows nothing about any individual server. Mirrors
 * Quarkus' {@code ShutdownContext}: a task list, not an orchestration of typed stages.
 */
@Internal
public final class ShutdownContext {

    private static final Logger log = LoggerFactory.getLogger(ShutdownContext.class);

    private final Deque<Runnable> tasks = new ArrayDeque<>();
    // Anchored deadline of the single shutdown budget (System.nanoTime()), 0 = none anchored.
    private volatile long deadlineNanos = 0L;

    private ShutdownContext() {}

    /** Creates an empty coordinator. */
    public static ShutdownContext create() {
        return new ShutdownContext();
    }

    /** Registers a shutdown task. Tasks run in reverse registration order. */
    public void addShutdownTask(Runnable task) {
        if (task == null) {
            throw new IllegalArgumentException("Shutdown task must not be null");
        }
        tasks.addFirst(task);
    }

    /**
     * Anchors the single shutdown budget: {@link #remaining()} counts down from THIS call, not from
     * each task's start, so registered tasks share one budget (the Spring/Quarkus single-budget
     * model) instead of each receiving a fresh full one — which would let one slow drain starve
     * every later teardown stage. The first call wins: a close() may be triggered by a JVM hook, an
     * explicit {@code close()}, or a test.
     */
    public void beginShutdown(java.time.Duration budget) {
        if (budget == null || budget.isNegative()) {
            return;
        }
        if (deadlineNanos == 0L) {
            deadlineNanos = System.nanoTime() + budget.toNanos();
        }
    }

    /**
     * Budget left of the window anchored by {@link #beginShutdown(Duration)}, or {@code null} when
     * no budget was anchored (tasks then fall back to their own configured timeout).
     */
    public java.time.Duration remaining() {
        long deadline = deadlineNanos;
        if (deadline == 0L) {
            return null;
        }
        long left = deadline - System.nanoTime();
        return left <= 0L ? java.time.Duration.ZERO : java.time.Duration.ofNanos(left);
    }

    /**
     * Runs every registered task (reverse registration order) and clears the list. A failing task
     * is logged and does not prevent the others from running.
     */
    public void runAll() {
        while (!tasks.isEmpty()) {
            Runnable task = tasks.removeFirst();
            try {
                task.run();
            } catch (Throwable t) {
                log.error("[Summer] Error during shutdown task: {}", t.getMessage(), t);
            }
        }
    }
}
