mport com.github.dropguard.summer.core.Internal;
package com.github.dropguard.summer.core;

mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
@Internal
import java.util.concurrent.atomic.AtomicBoolean;
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
/** Tracks the global state of the Summer application. */
mport com.github.dropguard.summer.core.Internal;
public final class ApplicationState {
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    private static final AtomicBoolean shuttingDown = new AtomicBoolean(false);
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    private ApplicationState() {}
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    /**
mport com.github.dropguard.summer.core.Internal;
     * Marks the application as shutting down. After this is called, readiness probes should return
mport com.github.dropguard.summer.core.Internal;
     * 503.
mport com.github.dropguard.summer.core.Internal;
     */
mport com.github.dropguard.summer.core.Internal;
    public static void beginShutdown() {
mport com.github.dropguard.summer.core.Internal;
        shuttingDown.set(true);
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    /**
mport com.github.dropguard.summer.core.Internal;
     * @return true if the application has received a termination signal.
mport com.github.dropguard.summer.core.Internal;
     */
mport com.github.dropguard.summer.core.Internal;
    public static boolean isShuttingDown() {
mport com.github.dropguard.summer.core.Internal;
        return shuttingDown.get();
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;
}
