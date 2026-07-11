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

	private Engine engine = null;
	private java.util.List<Class<? extends summer.web.Middleware>> middlewareEntries = new java.util.ArrayList<>();

	private SummerApplication() {
	}

	/**
	 * @deprecated Environment detection is now automatic. Use {@link #run(String[])} or {@link #start(String[])}.
	 */
	@Deprecated
	public static SummerApplication apply(Engine engine) {
		SummerApplication app = new SummerApplication();
		app.engine = engine;
		return app;
	}

	/**
	 * Main entry point. Automatically selects engine based on environment.
	 */
	public static BeanContainer run(String[] args) throws Exception {
		return new SummerApplication().start(args);
	}

	public SummerApplication apply(Class<? extends summer.web.Middleware> clazz) {
		this.middlewareEntries.add(clazz);
		return this;
	}

	public BeanContainer start(String[] args) throws Exception {
		Engine activeEngine = this.engine != null ? this.engine : summer.core.DiEngine.detectEngine();
		summer.web.GlobalMiddlewareChain chain = new summer.web.GlobalMiddlewareChain(this.middlewareEntries);
		BeanContainer context = DiEngine.create(activeEngine, chain);

		System.out.println(Banner.format(context.engine().name()));
		log.info("Starting Summer Application... [engine={}]", context.engine());

		for (var runner : context.getBeans(summer.core.ApplicationRunner.class)) {
			runner.run(context);
		}

		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			log.info("Shutting down BeanContainer...");
			try {
				context.close();
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}));

		log.info("Summer application started.");
		return context;
	}
}
