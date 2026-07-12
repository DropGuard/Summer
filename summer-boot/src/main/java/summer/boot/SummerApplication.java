package summer.boot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;
import summer.core.BeanContainer;
import summer.core.DiEngine;
import summer.core.Engine;

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

	private java.util.List<Class<? extends summer.web.Middleware>> middlewareEntries = new java.util.ArrayList<>();

	public SummerApplication() {
	}

	/**
	 * Main entry point.
	 */
	public static BeanContainer run(String[] args) throws Exception {
		return new SummerApplication().start(args);
	}

	public SummerApplication apply(Class<? extends summer.web.Middleware> clazz) {
		this.middlewareEntries.add(clazz);
		return this;
	}

	public BeanContainer start(String[] args) throws Exception {
		summer.web.GlobalMiddlewareChain chain = new summer.web.GlobalMiddlewareChain(this.middlewareEntries);
		BeanContainer context = DiEngine.create(chain);

		System.out.println(Banner.format(context.engine().name()));

		for (var runner : context.getBeans(summer.core.ApplicationRunner.class)) {
			runner.run(context);
		}

		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			summer.core.ApplicationState.beginShutdown();
			summer.core.config.ShutdownConfig shutdownConfig;
			try {
				shutdownConfig = context.getBean(summer.core.config.ShutdownConfig.class);
			} catch (Exception e) {
				shutdownConfig = new summer.core.config.ShutdownConfig(0L, 30000L);
			}

			long sleepMs = shutdownConfig.sleepMs() != null ? shutdownConfig.sleepMs() : 0L;
			if (sleepMs > 0) {
				log.info("Shutdown initiated. Sleeping for {} ms for traffic isolation...", sleepMs);
				try {
					Thread.sleep(sleepMs);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}

			log.info("Shutting down BeanContainer...");
			java.util.concurrent.ExecutorService shutdownExecutor = java.util.concurrent.Executors.newSingleThreadExecutor();
			try {
				long timeoutMs = shutdownConfig.timeoutMs() != null ? shutdownConfig.timeoutMs() : 30000L;
				shutdownExecutor.submit(() -> {
					try {
						context.close();
					} catch (Exception e) {
						log.error("Error during BeanContainer shutdown", e);
					}
				}).get(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
			} catch (java.util.concurrent.TimeoutException e) {
				log.warn("Shutdown grace period ({} ms) exceeded, forcing exit.", shutdownConfig.timeoutMs());
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
