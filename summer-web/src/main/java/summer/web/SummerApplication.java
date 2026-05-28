package summer.web;

import java.time.Duration;
import java.util.List;
import java.util.logging.LogManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;
import summer.core.ApplicationContext;
import summer.core.config.YamlConfigLoader;
import summer.scanner.runtime.RuntimeDiEngine;
import summer.validation.BodyValidator;
import summer.web.middleware.Middleware;

public class SummerApplication {

	private static final Logger log = LoggerFactory.getLogger(SummerApplication.class);

	static {
		LogManager.getLogManager().reset();
		SLF4JBridgeHandler.removeHandlersForRootLogger();
		SLF4JBridgeHandler.install();
	}

	private static final String BANNER = "\n" + "   _____                                         \n"
			+ "  / ___/__  ______ ___  ____ ___  ___  _____     \n"
			+ "  \\__ \\/ / / / __ `__ \\/ __ `__ \\/ _ \\/ ___/ \n"
			+ " ___/ / /_/ / / / / / / / / / / /  __/ /         \n"
			+ "/____/\\__,_/_/ /_/ /_/_/ /_/ /_/\\___/_/      \n"
			+ "                                                 \n"
			+ " :: Summer Framework ::                 (v0.1.0) \n";

	private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(5);

	private static summer.web.server.NettyHttpServer runningServer;
	private static ApplicationContext runningContext;

	public static void run(Class<?> mainClass, String[] args) throws Exception {
		builder(mainClass).run(args);
	}

	/**
	 * Stops the running application gracefully. Can be called from tests or
	 * programmatically.
	 */
	public static void stop() {
		if (runningServer == null)
			return;
		shutdown(runningServer, runningContext);
		runningServer = null;
		runningContext = null;
	}

	private static void shutdown(summer.web.server.NettyHttpServer server, ApplicationContext context) {
		log.info("Shutting down...");
		// 1. Stop accepting new requests, 2. Wait for in-flight requests
		server.stop(SHUTDOWN_TIMEOUT);
		// 3. Release resources: close thread pools, disconnect database, etc.
		context.destroy();
	}

	public static Builder builder(Class<?> mainClass) {
		return new Builder(mainClass);
	}

	public enum Engine {
		RUNTIME, AOT
	}

	public static class Builder {
		private final Class<?> mainClass;
		private int port = -1;
		private int connectionTimeout = -1;
		private int maxBodySize = -1;
		private int readTimeout = -1;
		private Engine engine = Engine.RUNTIME;

		private Builder(Class<?> mainClass) {
			this.mainClass = mainClass;
		}

		public Builder useRuntime() {
			this.engine = Engine.RUNTIME;
			return this;
		}

		public Builder useAot() {
			this.engine = Engine.AOT;
			return this;
		}

		public Builder port(int port) {
			this.port = port;
			return this;
		}

		public Builder connectionTimeout(int timeout) {
			this.connectionTimeout = timeout;
			return this;
		}

		public Builder maxBodySize(int maxBodySize) {
			this.maxBodySize = maxBodySize;
			return this;
		}

		public Builder readTimeout(int readTimeout) {
			this.readTimeout = readTimeout;
			return this;
		}

		public void run(String[] args) throws Exception {
			System.out.println(BANNER);
			log.info("Starting Summer Application...");

			ApplicationContext context = createContext();
			initRouter(context);
			ServerConfig config = resolveConfig();
			var server = startServer(context, config);
			runApplicationRunners(context);

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

		private ApplicationContext createContext() {
			if (engine == Engine.AOT) {
				return loadAotContext();
			}
			log.info("Using runtime scanning for DI...");
			return new RuntimeDiEngine().create(mainClass);
		}

		private ApplicationContext loadAotContext() {
			var it = java.util.ServiceLoader.load(ApplicationContext.class).iterator();
			if (!it.hasNext()) {
				throw new summer.core.exception.AotContextNotFoundException();
			}
			ApplicationContext ctx = it.next();
			ApplicationContext.init(ctx);
			log.info("AOT Context loaded via ServiceLoader.");
			return ctx;
		}

		private void initRouter(ApplicationContext context) {
			RouteRegistrar registrar = context.getBean(RouteRegistrar.class);
			registrar.registerControllers();
		}

		private ServerConfig resolveConfig() {
			ServerConfig defaults = YamlConfigLoader.loadOrDefault("application.yml", ServerConfig.class,
					ServerConfig.DEFAULT);
			return new ServerConfig(port != -1 ? port : defaults.port(),
					connectionTimeout != -1 ? connectionTimeout : defaults.connectionTimeout(),
					maxBodySize != -1 ? maxBodySize : defaults.maxBodySize(),
					readTimeout != -1 ? readTimeout : defaults.readTimeout());
		}

		private summer.web.server.NettyHttpServer startServer(ApplicationContext context, ServerConfig config) {
			List<Middleware> middlewares = context.getBeansOfType(Middleware.class).stream()
					.filter(m -> m.getClass().isAnnotationPresent(summer.web.annotation.GlobalMiddleware.class))
					.toList();
			List<BodyConverter> converters = context.getBeansOfType(BodyConverter.class);
			if (converters.isEmpty()) {
				converters = List.of(new JsonBodyConverter());
			}
			BodyValidator validator = findOptionalBean(context, BodyValidator.class);

			var server = new summer.web.server.NettyHttpServer(config, context.getBean(Router.class), middlewares,
					validator, converters);
			server.start();
			return server;
		}

		private void runApplicationRunners(ApplicationContext context) throws Exception {
			for (var runner : context.getBeansOfType(summer.core.ApplicationRunner.class)) {
				runner.run(context);
			}
		}

		private <T> T findOptionalBean(ApplicationContext context, Class<T> type) {
			try {
				return context.getBean(type);
			} catch (Exception e) {
				return null;
			}
		}
	}
}
