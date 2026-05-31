package summer.web;

import java.time.Duration;
import java.util.logging.LogManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;
import summer.core.ApplicationContext;
import summer.scanner.runtime.RuntimeDiEngine;

public class SummerApplication {

	private static final Logger log = LoggerFactory.getLogger(SummerApplication.class);
	private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(5);

	private static summer.web.server.NettyHttpServer runningServer;
	private static ApplicationContext runningContext;

	static {
		LogManager.getLogManager().reset();
		SLF4JBridgeHandler.removeHandlersForRootLogger();
		SLF4JBridgeHandler.install();
	}

	public static void run(Class<?> mainClass, String[] args) throws Exception {
		System.out.println(Banner.TEXT);
		log.info("Starting Summer Application...");

		ApplicationContext context = createContext(mainClass);
		context.getBean(RouteRegistrar.class).registerControllers();

		ServerConfig config = ServerConfig.fromYaml();
		var server = summer.web.server.NettyHttpServer.create(context, config);
		server.start();

		for (var runner : context.getBeansOfType(summer.core.ApplicationRunner.class)) {
			runner.run(context);
		}

		runningServer = server;
		runningContext = context;
		Runtime.getRuntime().addShutdownHook(new Thread(() -> shutdown(server, context)));

		log.info("Summer application started on http://localhost:{}", config.port());
		log.info("Press Ctrl+C to stop");

		try {
			Thread.currentThread().join();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	public static void stop() {
		if (runningServer == null)
			return;
		shutdown(runningServer, runningContext);
		runningServer = null;
		runningContext = null;
	}

	private static void shutdown(summer.web.server.NettyHttpServer server, ApplicationContext context) {
		log.info("Shutting down...");
		server.stop(SHUTDOWN_TIMEOUT);
		context.destroy();
	}

	private static ApplicationContext createContext(Class<?> mainClass) {
		var it = java.util.ServiceLoader.load(ApplicationContext.class).iterator();
		if (it.hasNext()) {
			ApplicationContext ctx = it.next();
			log.info("AOT Context loaded via ServiceLoader.");
			return ctx;
		}
		log.info("Using runtime scanning for DI...");
		return new RuntimeDiEngine().create(mainClass);
	}
}
