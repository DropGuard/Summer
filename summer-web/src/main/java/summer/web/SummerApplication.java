package summer.web;

import java.util.List;
import summer.core.ApplicationContext;
import summer.core.config.YamlConfigLoader;
import summer.web.middleware.Middleware;
import summer.web.server.HttpServer;

/**
 * Default entry point wrapper for initializing the Summer Framework. Abstracts
 * component scanning, DI resolution, and HTTP Server startup.
 */
public class SummerApplication {

	public static void run(Class<?> mainClass, String[] args) {
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

			// 3. Autowire Middlewares
			List<Middleware> middlewares = context.getBeansOfType(Middleware.class);

			// 4. Load server configuration from application.yml
			ServerConfig config = YamlConfigLoader.loadOrDefault("application.yml", ServerConfig.class,
					ServerConfig.DEFAULT);
			int port = config.port();

			// 5. Start HTTP server
			HttpServer server = HttpServer.create(port, router, middlewares);
			server.start();

			System.out.println("Summer application started on http://localhost:" + port);
			System.out.println("Press Ctrl+C to stop");

			// 6. Shutdown hook
			Runtime.getRuntime().addShutdownHook(new Thread(server::stop));

		} catch (Exception e) {
			System.err.println("Failed to start application: " + e.getMessage());
			e.printStackTrace();
			System.exit(1);
		}
	}
}
