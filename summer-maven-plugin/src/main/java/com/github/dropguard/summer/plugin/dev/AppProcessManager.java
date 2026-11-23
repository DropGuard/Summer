package com.github.dropguard.summer.plugin.dev;

import java.io.File;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/** Manages the lifecycle of the Summer Application child JVM. */
public class AppProcessManager {
    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(AppProcessManager.class);

    /**
     * How long a stopped app JVM gets for its graceful shutdown before it is force-killed. Dev mode
     * deliberately does NOT honour the app's {@code shutdown.timeout-ms} (default 10s, sized for
     * production drains): every source change restarts the app, and a 10s wait per edit would
     * defeat the eager-kill/lazy-reboot loop. Shutdown hooks and {@code close()} still run; only
     * work that needs longer than this is cut short.
     *
     * <p>The stop chain is CLI → Maven → app, each layer bounding the one below: {@code summer dev}
     * waits {@code devStopTimeout} (5s, summer-cli {@code pkg/runner/maven.go}) for Maven, whose
     * shutdown hook calls {@link #kill()} with this grace. Keep this below the CLI's budget so the
     * app's hooks get their full grace before the CLI escalates to killing the process tree.
     */
    static final Duration STOP_GRACE = Duration.ofSeconds(2);

    private Process currentProcess;

    public AppProcessManager() {}

    public void start(int port, String mainClass, String classpath) throws Exception {
        kill(); // Ensure previous is dead

        String javaHome = System.getProperty("java.home");
        String javaBin = javaHome + File.separator + "bin" + File.separator + "java";

        ProcessBuilder pb = new ProcessBuilder(javaBin, "-cp", classpath, mainClass);
        // The magic handshake channel
        pb.environment().put("SUMMER_DEV_PORT", String.valueOf(port));
        // -D properties do not cross process boundaries; the child inherits only the
        // environment, so the engine override travels as SUMMER_ENGINE (the boot reads it
        // with the Spring/Quarkus precedence: -Dsummer.engine > SUMMER_ENGINE > yml > default).
        String engine = System.getProperty("summer.engine");
        if (engine != null && !engine.isBlank()) {
            pb.environment().put("SUMMER_ENGINE", engine);
        }
        // Route child logs to parent console seamlessly
        pb.inheritIO();

        log.info("[Summer] Booting Summer App (Main: " + mainClass + ", Port: " + port + ")...");
        currentProcess = pb.start();
    }

    public void kill() {
        if (currentProcess != null && currentProcess.isAlive()) {
            log.info("[Summer] Killing previous Summer App JVM...");
            currentProcess.destroy();
            try {
                if (!currentProcess.waitFor(STOP_GRACE.toMillis(), TimeUnit.MILLISECONDS)) {
                    currentProcess.destroyForcibly();
                }
            } catch (InterruptedException e) {
                currentProcess.destroyForcibly();
                Thread.currentThread().interrupt();
            }
            currentProcess = null;
        }
    }
}
