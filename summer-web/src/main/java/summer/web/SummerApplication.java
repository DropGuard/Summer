package summer.web;

import java.util.List;
import summer.core.ApplicationContext;
import summer.core.config.YamlConfigLoader;
import summer.validation.BodyValidator;
import summer.web.middleware.Middleware;
import summer.web.server.HttpServer;
import summer.web.server.HttpConnectionHandler;

/**
 * Default entry point wrapper for initializing the Summer Framework. Abstracts
 * component scanning, DI resolution, and HTTP Server startup.
 */
public class SummerApplication {

	public static void run(Class<?> mainClass, String[] args) {
		builder(mainClass).run(args);
	}

	public static Builder builder(Class<?> mainClass) {
		return new Builder(mainClass);
	}

	public static class Builder {
		private final Class<?> mainClass;
		private int port = -1;
		private int connectionTimeout = -1;
		private int maxBodySize = -1;
		private int readTimeout = -1;

		private Builder(Class<?> mainClass) {
			this.mainClass = mainClass;
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

		public void run(String[] args) {
			try {
				// 1. Scan components deriving from the main class package
				String basePackage = mainClass.getPackageName();
				System.out.println("Starting Summer Application...");
				ApplicationContext.scan(basePackage);
				ApplicationContext context = ApplicationContext.getInstance();

				// 2. Initialize router and register routes
				Router router = context.getBean(Router.class);
				AnnotationRouterAdapter routerAdapter = context.getBean(AnnotationRouterAdapter.class);
				routerAdapter.registerControllers();

				// 3. Autowire Middlewares, Validator, and Converters
				List<Middleware> middlewares = context.getBeansOfType(Middleware.class);
				List<BodyConverter> converters = context.getBeansOfType(BodyConverter.class);
				
				// Ensure at least JSON converter is present if none found
				if (converters.isEmpty()) {
					converters = List.of(new JsonBodyConverter());
				}

				BodyValidator validator = null;
				try {
					validator = context.getBean(BodyValidator.class);
				} catch (Exception e) {
					// Validator is optional
				}

				// 4. Load server configuration from application.yml and apply overrides
				ServerConfig config = YamlConfigLoader.loadOrDefault("application.yml", ServerConfig.class,
						ServerConfig.DEFAULT);
				
				int finalPort = this.port != -1 ? this.port : config.port();
				int finalTimeout = this.connectionTimeout != -1 ? this.connectionTimeout : config.connectionTimeout();
				int finalMaxBody = this.maxBodySize != -1 ? this.maxBodySize : config.maxBodySize();
				int finalReadTimeout = this.readTimeout != -1 ? this.readTimeout : config.readTimeout();
				
				ServerConfig finalConfig = new ServerConfig(finalPort, finalTimeout, finalMaxBody, finalReadTimeout);

				// 5. Start HTTP server
				HttpServer server = HttpServer.create(finalConfig, router, middlewares, validator, converters);
				server.start();

				System.out.println("Summer application started on http://localhost:" + finalPort);
				System.out.println("Press Ctrl+C to stop");

				// 6. Shutdown hook
				Runtime.getRuntime().addShutdownHook(new Thread(server::stop));

			} catch (Exception e) {
				System.err.println("Failed to start application: " + e.getMessage());
				if (e instanceof RuntimeException re) throw re;
				throw new RuntimeException("Application startup failed", e);
			}
		}
	}
}
