package summer.core;

import java.util.ArrayDeque;
import java.util.Deque;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Independent coordinator for application teardown. Input drivers (HTTP / gRPC
 * servers, ...) register a shutdown {@link Runnable} at startup; the container
 * runs every task in reverse registration order on {@link #runAll()}, before
 * the remaining {@link AutoCloseable} beans are closed.
 *
 * <p>
 * This is the single convergence point for "how do I stop myself" — each
 * registrant encapsulates its own staging (stop accepting, drain in-flight,
 * release resources) inside the task, so the coordinator stays
 * transport-agnostic and knows nothing about any individual server. Mirrors
 * Quarkus' {@code ShutdownContext}: a task list, not an orchestration of typed
 * stages.
 */
public final class ShutdownContext {

	private static final Logger log = LoggerFactory.getLogger(ShutdownContext.class);

	private final Deque<Runnable> tasks = new ArrayDeque<>();

	private ShutdownContext() {
	}

	/**
	 * Creates an empty coordinator.
	 */
	public static ShutdownContext create() {
		return new ShutdownContext();
	}

	/**
	 * Registers a shutdown task. Tasks run in reverse registration order.
	 */
	public void addShutdownTask(Runnable task) {
		if (task == null) {
			throw new IllegalArgumentException("Shutdown task must not be null");
		}
		tasks.addFirst(task);
	}

	/**
	 * Runs every registered task (reverse registration order) and clears the list.
	 * A failing task is logged and does not prevent the others from running.
	 */
	public void runAll() {
		while (!tasks.isEmpty()) {
			Runnable task = tasks.removeFirst();
			try {
				task.run();
			} catch (Exception e) {
				log.error("[Summer] Error during shutdown task: {}", e.getMessage(), e);
			}
		}
	}
}
