package summer.web.server;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
import summer.web.annotation.GlobalMiddleware;

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

	/**
	 * Type key under which {@code SummerApplication} registers the ordered list of
	 * middleware classes declared via {@code apply(...)}. A plain JDK {@code List}
	 * type is used as the bean key so no dedicated carrier type is needed.
	 */
	@SuppressWarnings("unchecked")
	private static final Class<List<Class<? extends summer.web.Middleware>>> APPLY_LIST_TYPE = (Class<List<Class<? extends summer.web.Middleware>>>) (Class<?>) List.class;

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

		List<summer.web.Middleware> globalMiddlewares = collectGlobalMiddlewares(context);

		runningServer = NettyHttpServer.create(context, config, httpRouter, wsRouter, exceptionRegistry,
				globalMiddlewares);
		runningServer.start();
		actualPort = runningServer.getPort();
	}

	/**
	 * Collects global middleware in application order:
	 * <ol>
	 * <li>middleware classes declared via {@code SummerApplication.apply(...)} —
	 * explicit, in declaration order (registered as a plain
	 * {@code List<Class<? extends Middleware>>} bean);</li>
	 * <li>{@code @GlobalMiddleware}-annotated {@code Middleware} beans, in
	 * container registration order.</li>
	 * </ol>
	 * Explicit declarations take precedence because the caller ordered them by
	 * hand; annotated beans fill in everything else. A middleware bean is only
	 * added once even if both mechanisms reference it.
	 */
	private List<summer.web.Middleware> collectGlobalMiddlewares(BeanContainer context) {
		List<summer.web.Middleware> result = new ArrayList<>();
		Set<Class<?>> added = new HashSet<>();

		// 1. Explicit (apply-ordered) declarations
		try {
			for (Class<? extends summer.web.Middleware> c : context.getBean(APPLY_LIST_TYPE)) {
				if (added.add(c)) {
					result.add(context.getBean(c));
				}
			}
		} catch (summer.core.exception.NoSuchBeanException ignored) {
			// No SummerApplication.apply(...) was used — fine.
		}

		// 2. @GlobalMiddleware-annotated beans (registration order)
		for (summer.web.Middleware m : context.getBeans(summer.web.Middleware.class)) {
			if (m.getClass().isAnnotationPresent(GlobalMiddleware.class) && added.add(m.getClass())) {
				result.add(m);
			}
		}
		return result;
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
