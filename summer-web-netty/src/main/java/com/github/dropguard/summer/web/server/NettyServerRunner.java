package com.github.dropguard.summer.web.server;

import com.github.dropguard.summer.core.ApplicationRunner;
import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.runtime.RuntimeWebConfiguration;
import com.github.dropguard.summer.web.ExceptionHandlerRegistrar;
import com.github.dropguard.summer.web.ExceptionRegistry;
import com.github.dropguard.summer.web.HttpRouter;
import com.github.dropguard.summer.web.RouteRegistrar;
import com.github.dropguard.summer.web.RouterRegistry;
import com.github.dropguard.summer.web.ServerConfig;
import com.github.dropguard.summer.web.WsRouter;
import com.github.dropguard.summer.web.annotation.GlobalMiddleware;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Netty-based application runner that starts the HTTP server.
 *
 * <p>
 * This is a framework infrastructure bean provided by
 * {@link RuntimeWebConfiguration}.
 * </p>
 */
public class NettyServerRunner implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(NettyServerRunner.class);

	/**
	 * Type key under which {@code SummerApplication} registers the ordered list of
	 * middleware classes declared via {@code apply(...)}. A plain JDK {@code List}
	 * type is used as the bean key so no dedicated carrier type is needed.
	 */
	@SuppressWarnings("unchecked")
	private static final Class<List<Class<? extends com.github.dropguard.summer.web.Middleware>>> APPLY_LIST_TYPE = (Class<List<Class<? extends com.github.dropguard.summer.web.Middleware>>>) (Class<?>) List.class;

	private final RouterRegistry routerRegistry;
	private final ServerConfig config;
	private NettyHttpServer runningServer;
	// Port is a property of THIS server instance, not JVM-global state. Each
	// container owns its own runner, so the bound port is read through the
	// instance — a static field would be shared across every concurrent IT class
	// in the same JVM and race on the value (the original port-conflict bug).
	private int actualPort = -1;

	public NettyServerRunner(RouterRegistry routerRegistry, ServerConfig config) {
		this.routerRegistry = routerRegistry;
		this.config = config;
	}

	@Override
	public void run(BeanContainer context) throws Exception {
		ExceptionRegistry exceptionRegistry = buildExceptionRegistry(context);
		HttpRouter httpRouter = buildHttpRouter(context);
		WsRouter wsRouter = buildWsRouter(context);

		List<com.github.dropguard.summer.web.Middleware> globalMiddlewares = collectGlobalMiddlewares(context);

		runningServer = NettyHttpServer.create(context, config, httpRouter, wsRouter, exceptionRegistry,
				globalMiddlewares);
		runningServer.start();
		this.actualPort = runningServer.getPort();

		long timeoutMs = context.getShutdownConfig().timeoutMs();
		context.addShutdownTask(() -> shutdown(java.time.Duration.ofMillis(timeoutMs)));
	}

	/**
	 * The actual port this server instance bound to. Valid after
	 * {@link #run(BeanContainer)} has started the server; returns -1 before start
	 * or after {@link #close()}.
	 *
	 * <p>
	 * When {@code server.port} is 0 the OS assigns a free ephemeral port, and this
	 * returns that resolved value — the correct way for a test to learn which port
	 * its container's server is listening on. This is an instance method so a test
	 * obtains its runner via {@code context.getBean(NettyServerRunner.class)} and
	 * always sees its OWN port, never a sibling IT class's.
	 * </p>
	 */
	public int getPort() {
		return actualPort;
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
	private List<com.github.dropguard.summer.web.Middleware> collectGlobalMiddlewares(BeanContainer context) {
		List<com.github.dropguard.summer.web.Middleware> result = new ArrayList<>();
		Set<Class<?>> added = new HashSet<>();

		// 1. Explicit (apply-ordered) declarations
		try {
			for (Class<? extends com.github.dropguard.summer.web.Middleware> c : context.getBean(APPLY_LIST_TYPE)) {
				if (added.add(c)) {
					result.add(context.getBean(c));
				}
			}
		} catch (com.github.dropguard.summer.core.exception.NoSuchBeanException ignored) {
			// No SummerApplication.apply(...) was used — fine.
		}

		// 2. @GlobalMiddleware-annotated beans (registration order)
		for (com.github.dropguard.summer.web.Middleware m : context
				.getBeans(com.github.dropguard.summer.web.Middleware.class)) {
			if (m.getClass().isAnnotationPresent(GlobalMiddleware.class) && added.add(m.getClass())) {
				result.add(m);
			}
		}
		return result;
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

		for (com.github.dropguard.summer.web.WsRouteProvider provider : context
				.getBeans(com.github.dropguard.summer.web.WsRouteProvider.class)) {
			provider.provide(builder);
		}

		return builder.build();
	}

	private void shutdown(java.time.Duration timeout) {
		if (runningServer == null) {
			return;
		}
		log.info("Shutting down Netty Server...");
		runningServer.shutdown(timeout);
		runningServer = null;
	}

	/**
	 * Convenience for direct/test use: stops the server immediately (zero drain
	 * timeout). The container drives the same staging via the shutdown task
	 * registered in {@link #run(BeanContainer)}, bounded by
	 * {@code com.github.dropguard.summer.shutdown.timeout-ms}.
	 */
	public void stop() {
		shutdown(java.time.Duration.ZERO);
	}
}
