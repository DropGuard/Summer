package summer.web.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import summer.core.ApplicationContext;
import summer.core.ApplicationRunner;
import summer.core.Engine;
import summer.runtime.RuntimeWebConfiguration;
import summer.web.ExceptionHandlerRegistrar;
import summer.web.ExceptionRegistry;
import summer.web.HttpRouter;
import summer.web.RouteRegistrar;
import summer.web.RouterRegistry;
import summer.web.RouterType;
import summer.web.ServerConfig;
import summer.web.WsRouter;

/**
 * Netty-based application runner that starts the HTTP server.
 *
 * <p>
 * This is a framework infrastructure bean provided by
 * {@link RuntimeWebConfiguration}.
 * </p>
 */
public class NettyServerRunner implements ApplicationRunner, AutoCloseable {

	private static final Logger log = LoggerFactory.getLogger(NettyServerRunner.class);
	private final RouterRegistry routerRegistry;
	private final ServerConfig config;
	private NettyHttpServer runningServer;
	private static volatile int actualPort = -1;

	public NettyServerRunner(RouterRegistry routerRegistry, ServerConfig config) {
		this.routerRegistry = routerRegistry;
		this.config = config;
	}

	@Override
	public void run(ApplicationContext context) throws Exception {
		Engine engine = context.engine();

		ExceptionRegistry exceptionRegistry = buildExceptionRegistry(context, engine);
		HttpRouter httpRouter = buildHttpRouter(context, engine);
		WsRouter wsRouter = buildWsRouter(context, engine);
		runningServer = NettyHttpServer.create(context, config, httpRouter, wsRouter, exceptionRegistry);
		runningServer.start();
		actualPort = runningServer.getPort();
	}

	/**
	 * Returns the actual port the server bound to. Useful when {@code server.port}
	 * is 0 (random port).
	 */
	public static int getActualPort() {
		return actualPort;
	}

	private ExceptionRegistry buildExceptionRegistry(ApplicationContext context, Engine engine) {
		ExceptionRegistry registry = new ExceptionRegistry();
		for (ExceptionHandlerRegistrar registrar : context.getBeans(ExceptionHandlerRegistrar.class)) {
			registrar.registerHandlers(registry, context);
		}
		return registry;
	}

	private HttpRouter buildHttpRouter(ApplicationContext context, Engine engine) {
		// Runtime uses MAP (simple, easy to debug), AOT uses RADIX_TREE
		RouterType routerType = engine == Engine.RUNTIME ? RouterType.MAP : RouterType.RADIX_TREE;
		var builder = new HttpRouter.Builder(routerType, routerRegistry);

		for (RouteRegistrar registrar : context.getBeans(RouteRegistrar.class)) {
			registrar.registerControllers(builder, context);
		}

		return builder.build();
	}

	private WsRouter buildWsRouter(ApplicationContext context, Engine engine) {
		// Runtime uses MAP (simple, easy to debug), AOT uses RADIX_TREE
		RouterType routerType = engine == Engine.RUNTIME ? RouterType.MAP : RouterType.RADIX_TREE;
		var builder = new WsRouter.Builder(routerType, routerRegistry);

		for (summer.web.WsRouteProvider provider : context.getBeans(summer.web.WsRouteProvider.class)) {
			provider.provide(builder);
		}

		return builder.build();
	}

	@Override
	public void close() throws Exception {
		if (runningServer != null) {
			log.info("Shutting down Netty Server...");
			runningServer.stop(java.time.Duration.ofSeconds(5));
			runningServer = null;
		}
	}
}
