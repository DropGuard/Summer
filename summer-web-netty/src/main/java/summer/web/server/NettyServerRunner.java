package summer.web.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import summer.core.ApplicationRunner;
import summer.core.BeanContainer;
import summer.runtime.RuntimeWebConfiguration;
import summer.web.ExceptionHandlerRegistrar;
import summer.web.ExceptionRegistry;
import summer.web.HttpRouter;
import summer.web.RouteRegistrar;
import summer.web.RouterRegistry;
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
	private final summer.core.config.ShutdownConfig shutdownConfig;
	private NettyHttpServer runningServer;
	private static volatile int actualPort = -1;

	public NettyServerRunner(RouterRegistry routerRegistry, ServerConfig config,
			summer.core.config.ShutdownConfig shutdownConfig) {
		this.routerRegistry = routerRegistry;
		this.config = config;
		this.shutdownConfig = shutdownConfig;
	}

	@Override
	public void run(BeanContainer context) throws Exception {
		ExceptionRegistry exceptionRegistry = buildExceptionRegistry(context);
		HttpRouter httpRouter = buildHttpRouter(context);
		WsRouter wsRouter = buildWsRouter(context);

		summer.web.GlobalMiddlewareChain chain = context.getBean(summer.web.GlobalMiddlewareChain.class);
		java.util.List<summer.web.Middleware> globalMiddlewares = new java.util.ArrayList<>();
		for (Class<? extends summer.web.Middleware> c : chain.middlewares()) {
			globalMiddlewares.add(context.getBean(c));
		}

		runningServer = NettyHttpServer.create(context, config, httpRouter, wsRouter, exceptionRegistry,
				globalMiddlewares);
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

	private ExceptionRegistry buildExceptionRegistry(BeanContainer context) {
		ExceptionRegistry registry = new ExceptionRegistry();
		for (ExceptionHandlerRegistrar registrar : context.getBeans(ExceptionHandlerRegistrar.class)) {
			registrar.registerHandlers(registry, context);
		}
		return registry;
	}

	private HttpRouter buildHttpRouter(BeanContainer context) {
		var builder = new HttpRouter.Builder(config.routerType(), routerRegistry);

		for (RouteRegistrar registrar : context.getBeans(RouteRegistrar.class)) {
			registrar.registerControllers(builder, context);
		}

		return builder.build();
	}

	private WsRouter buildWsRouter(BeanContainer context) {
		var builder = new WsRouter.Builder(config.routerType(), routerRegistry);

		for (summer.web.WsRouteProvider provider : context.getBeans(summer.web.WsRouteProvider.class)) {
			provider.provide(builder);
		}

		return builder.build();
	}

	@Override
	public void close() throws Exception {
		if (runningServer != null) {
			log.info("Shutting down Netty Server...");
			runningServer.stop(java.time.Duration
					.ofMillis(shutdownConfig.timeoutMs() != null ? shutdownConfig.timeoutMs() : 30000L));
			runningServer = null;
		}
	}
}
