mport com.github.dropguard.summer.core.Internal;
package com.github.dropguard.summer.core;

mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
import java.util.ArrayDeque;
mport com.github.dropguard.summer.core.Internal;
import java.util.Deque;
mport com.github.dropguard.summer.core.Internal;
import org.slf4j.Logger;
mport com.github.dropguard.summer.core.Internal;
import org.slf4j.LoggerFactory;
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
/**
mport com.github.dropguard.summer.core.Internal;
 * Independent coordinator for application teardown. Input drivers (HTTP / gRPC servers, ...)
@Internal
mport com.github.dropguard.summer.core.Internal;
 * register a shutdown {@link Runnable} at startup; the container runs every task in reverse
mport com.github.dropguard.summer.core.Internal;
 * registration order on {@link #runAll()}, before the remaining {@link AutoCloseable} beans are
mport com.github.dropguard.summer.core.Internal;
 * closed.
mport com.github.dropguard.summer.core.Internal;
 *
mport com.github.dropguard.summer.core.Internal;
 * <p>This is the single convergence point for "how do I stop myself" — each registrant encapsulates
mport com.github.dropguard.summer.core.Internal;
 * its own staging (stop accepting, drain in-flight, release resources) inside the task, so the
mport com.github.dropguard.summer.core.Internal;
 * coordinator stays transport-agnostic and knows nothing about any individual server. Mirrors
mport com.github.dropguard.summer.core.Internal;
 * Quarkus' {@code ShutdownContext}: a task list, not an orchestration of typed stages.
mport com.github.dropguard.summer.core.Internal;
 */
mport com.github.dropguard.summer.core.Internal;
public final class ShutdownContext {
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    private static final Logger log = LoggerFactory.getLogger(ShutdownContext.class);
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    private final Deque<Runnable> tasks = new ArrayDeque<>();
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    private ShutdownContext() {}
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    /** Creates an empty coordinator. */
mport com.github.dropguard.summer.core.Internal;
    public static ShutdownContext create() {
mport com.github.dropguard.summer.core.Internal;
        return new ShutdownContext();
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    /** Registers a shutdown task. Tasks run in reverse registration order. */
mport com.github.dropguard.summer.core.Internal;
    public void addShutdownTask(Runnable task) {
mport com.github.dropguard.summer.core.Internal;
        if (task == null) {
mport com.github.dropguard.summer.core.Internal;
            throw new IllegalArgumentException("Shutdown task must not be null");
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
        tasks.addFirst(task);
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    /**
mport com.github.dropguard.summer.core.Internal;
     * Runs every registered task (reverse registration order) and clears the list. A failing task
mport com.github.dropguard.summer.core.Internal;
     * is logged and does not prevent the others from running.
mport com.github.dropguard.summer.core.Internal;
     */
mport com.github.dropguard.summer.core.Internal;
    public void runAll() {
mport com.github.dropguard.summer.core.Internal;
        while (!tasks.isEmpty()) {
mport com.github.dropguard.summer.core.Internal;
            Runnable task = tasks.removeFirst();
mport com.github.dropguard.summer.core.Internal;
            try {
mport com.github.dropguard.summer.core.Internal;
                task.run();
mport com.github.dropguard.summer.core.Internal;
            } catch (Exception e) {
mport com.github.dropguard.summer.core.Internal;
                log.error("[Summer] Error during shutdown task: {}", e.getMessage(), e);
mport com.github.dropguard.summer.core.Internal;
            }
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;
}
