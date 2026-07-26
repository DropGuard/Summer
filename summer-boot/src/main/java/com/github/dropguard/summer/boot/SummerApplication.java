package com.github.dropguard.summer.boot;

import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.core.DiEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

/**
 * Single entry point for Summer applications.
 *
 * <pre>{@code
 * // Auto-detects environment: uses RUNTIME in IDE, AOT in production (jar)
 * SummerApplication.run(args);
 * }</pre>
 */
public final class SummerApplication {

	private static final Logger log = LoggerFactory.getLogger(SummerApplication.class);

	static {
		SLF4JBridgeHandler.removeHandlersForRootLogger();
		SLF4JBridgeHandler.install();
	}

	private java.util.List<Class<? extends com.github.dropguard.summer.web.Middleware>> middlewareEntries = new java.util.ArrayList<>();

	public SummerApplication() {
	}

	/**
	 * Main entry point.
	 */
	public static BeanContainer run(String[] args) throws Exception {
		return new SummerApplication().start(args);
	}

	public SummerApplication apply(Class<? extends com.github.dropguard.summer.web.Middleware> clazz) {
		this.middlewareEntries.add(clazz);
		return this;
	}

	public BeanContainer start(String[] args) throws Exception {
		// The ordered list of middleware classes declared via apply(...) is passed
		// as a boot-time external bean (keyed by the plain List type) so the web
		// server runner can apply them in declaration order. Middleware beans
		// annotated with @GlobalMiddleware are collected automatically without this.
		BeanContainer context = DiEngine.create(this.middlewareEntries);

		System.out.println(Banner.format(context.engine().name()));

		for (var runner : context.getBeans(com.github.dropguard.summer.core.ApplicationRunner.class)) {
			runner.run(context);
		}

		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			// Signal shutdown first so the readiness probe (/health/ready) returns
			// 503 and the load balancer stops routing before the server stops
			// accepting. The drain window is then LB-polling-driven, bounded by
			// com.github.dropguard.summer.shutdown.timeout-ms.
			com.github.dropguard.summer.core.ApplicationState.beginShutdown();

			// BeanContainer.close() runs each registered shutdown task (servers stop
			// accepting, drain in-flight, release resources) in reverse order, then
			// closes the remaining AutoCloseable beans. This hook only guards the
			// whole teardown with a JVM-level worst-case timeout so a stuck bean
			// can't hang exit.
			log.info("Shutting down BeanContainer...");
			java.util.concurrent.ExecutorService shutdownExecutor = java.util.concurrent.Executors
					.newSingleThreadExecutor();
			try {
				shutdownExecutor.submit(() -> {
					try {
						context.close();
					} catch (Exception e) {
						log.error("Error during BeanContainer shutdown", e);
					}
				}).get(30, java.util.concurrent.TimeUnit.SECONDS);
			} catch (java.util.concurrent.TimeoutException e) {
				log.warn("Shutdown grace period (30s) exceeded, forcing exit.");
			} catch (Exception e) {
				log.error("Error waiting for shutdown", e);
			} finally {
				shutdownExecutor.shutdownNow();
			}
		}));

		log.info("Summer application started.");
		return context;
	}
}
