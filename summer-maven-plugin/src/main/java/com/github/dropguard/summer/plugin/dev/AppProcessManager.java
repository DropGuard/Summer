package com.github.dropguard.summer.plugin.dev;

import java.io.File;
import java.util.concurrent.TimeUnit;

/** Manages the lifecycle of the Summer Application child JVM. */
public class AppProcessManager {
    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(AppProcessManager.class);
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
                if (!currentProcess.waitFor(2, TimeUnit.SECONDS)) {
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
