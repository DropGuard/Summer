package summer.boot;

import java.util.logging.LogManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;
import summer.core.ApplicationContext;
import summer.runtime.RuntimeApplicationContext;

public class SummerApplication {

	private static final Logger log = LoggerFactory.getLogger(SummerApplication.class);

	private final ApplicationContext context;

	public SummerApplication(ApplicationContext context) {
		this.context = context;
	}

	/**
	 * Convenience constructor using runtime scanning.
	 */
	public SummerApplication(Class<?> mainClass) {
		this(RuntimeApplicationContext.create(mainClass));
	}

	static {
		LogManager.getLogManager().reset();
		SLF4JBridgeHandler.removeHandlersForRootLogger();
		SLF4JBridgeHandler.install();
	}

	/**
	 * Run with explicit ApplicationContext (e.g. AOT generated).
	 */
	public static ApplicationContext run(Class<?> mainClass, String[] args, ApplicationContext context)
			throws Exception {
		return new SummerApplication(context).run(args);
	}

	/**
	 * Run with runtime scanning (default).
	 */
	public static ApplicationContext run(Class<?> mainClass, String[] args) throws Exception {
		return new SummerApplication(mainClass).run(args);
	}

	public ApplicationContext run(String[] args) throws Exception {
		System.out.println(Banner.format()); // Intentional: banner goes to stdout
		log.info("Starting Summer Application...");

		// ==========================================
		// Execute Application Runners
		// ==========================================
		for (var runner : context.getBeans(summer.core.ApplicationRunner.class)) {
			runner.run(context);
		}

		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			log.info("Shutting down ApplicationContext...");
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
