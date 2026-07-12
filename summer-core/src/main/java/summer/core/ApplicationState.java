package summer.core;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tracks the global state of the Summer application.
 */
public final class ApplicationState {

	private static final AtomicBoolean shuttingDown = new AtomicBoolean(false);

	private ApplicationState() {
	}

	/**
	 * Marks the application as shutting down. After this is called, readiness
	 * probes should return 503.
	 */
	public static void beginShutdown() {
		shuttingDown.set(true);
	}

	/**
	 * @return true if the application has received a termination signal.
	 */
	public static boolean isShuttingDown() {
		return shuttingDown.get();
	}
}
