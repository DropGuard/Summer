package com.github.dropguard.summer.plugin;

import java.io.File;
import java.util.concurrent.TimeUnit;
import org.apache.maven.plugin.logging.Log;

/**
 * Manages the lifecycle of the Summer Application child JVM.
 */
public class AppProcessManager {
	private final Log log;
	private Process currentProcess;

	public AppProcessManager(Log log) {
		this.log = log;
	}

	public void start(int port, String mainClass, String classpath) throws Exception {
		kill(); // Ensure previous is dead

		String javaHome = System.getProperty("java.home");
		String javaBin = javaHome + File.separator + "bin" + File.separator + "java";

		ProcessBuilder pb = new ProcessBuilder(javaBin, "-cp", classpath, mainClass);
		// The magic handshake channel
		pb.environment().put("SUMMER_DEV_PORT", String.valueOf(port));
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
