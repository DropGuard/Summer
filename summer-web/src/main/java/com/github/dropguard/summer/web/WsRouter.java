package com.github.dropguard.summer.web;

import com.github.dropguard.summer.web.websocket.WebSocketContext;
import com.github.dropguard.summer.web.websocket.WebSocketHandler;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Interface for WebSocket routing.
 *
 * <p>
 * A router maps incoming WebSocket upgrade requests to their corresponding
 * handlers based on path. This interface is <strong>immutable by
 * design</strong> -- it exposes only the {@link #routeWs(String)} method for
 * dispatching requests. Route registration is done via the {@link Builder}
 * inner class.
 * </p>
 *
 * <h2>Building Routes</h2>
 *
 * <pre>{@code
 * WsRouter router = new WsRouter.Builder(RadixWsRouter::new).mount(ChatRoutes::ws).build();
 * }</pre>
 *
 * @see Builder
 */
public interface WsRouter {

	/**
	 * Routes a WebSocket upgrade request to the appropriate handler.
	 *
	 * @param path
	 *            the request path
	 * @return the WebSocket match containing handler and path parameters, or null
	 *         if no route matches
	 */
	WsMatch routeWs(String path);

	/**
	 * A WebSocket route entry containing path and handler. Used to pass route
	 * definitions to router implementations.
	 */
	record WsRoute(String path, WebSocketHandler handler) {
	}

	/**
	 * Represents the result of matching a WebSocket route.
	 */
	public record WsMatch(WebSocketHandler handler, Map<String, String> pathParams) {
	}

	/**
	 * Builder for WebSocket routers. Provides a fluent DSL for defining WebSocket
	 * routes.
	 *
	 * <p>
	 * Collects WebSocket routes and builds an immutable router on {@link #build()}.
	 * The router implementation is determined by the factory function passed to the
	 * constructor.
	 * </p>
	 *
	 * <pre>{@code
	 * WsRouter router = new WsRouter.Builder(RadixWsRouter::new).bind("/ws/chat", ws -> {
	 * 	ws.onConnect(ctx -> log.info("Connected")).onMessage(msg -> handleMessage(msg));
	 * }).mount(ChatRoutes::api).build();
	 * }</pre>
	 */
	class Builder {

		private final Function<List<WsRoute>, WsRouter> routerFactory;
		private final List<WsRoute> routes = new ArrayList<>();

		/**
		 * Creates a new Builder with the specified router factory.
		 *
		 * @param routerFactory
		 *            a function that creates the router implementation from the route
		 *            list
		 */
		public Builder(Function<List<WsRoute>, WsRouter> routerFactory) {
			this.routerFactory = routerFactory;
		}

		/**
		 * Creates a new Builder with the specified router type.
		 *
		 * <p>
		 * The router factory is looked up from the provided {@link RouterRegistry}.
		 * </p>
		 *
		 * @param type
		 *            the router type to use
		 * @param registry
		 *            the router registry to look up the factory from
		 * @throws IllegalArgumentException
		 *             if no factory is registered for the type
		 */
		public Builder(RouterType type, RouterRegistry registry) {
			this(registry.wsFactory(type));
		}

		/**
		 * Registers a WebSocket handler directly for the given path.
		 *
		 * @param path
		 *            the path to bind (e.g., "/ws/chat")
		 * @param handler
		 *            the WebSocket handler
		 * @return this builder for chaining
		 */
		public Builder ws(String path, WebSocketHandler handler) {
			routes.add(new WsRoute(path, handler));
			return this;
		}

		/**
		 * Binds WebSocket lifecycle handlers to a specific path.
		 *
		 * @param path
		 *            the path to bind (e.g., "/ws/chat")
		 * @param lifecycleConfig
		 *            callback to configure lifecycle hooks
		 * @return this builder for chaining
		 */
		public Builder bind(String path, Consumer<WsLifecycleBuilder> lifecycleConfig) {
			DefaultWsLifecycleBuilder lifecycleBuilder = new DefaultWsLifecycleBuilder();
			lifecycleConfig.accept(lifecycleBuilder);
			WebSocketHandler handler = lifecycleBuilder.build();
			routes.add(new WsRoute(path, handler));
			return this;
		}

		/**
		 * Mounts a WebSocket route module.
		 *
		 * @param module
		 *            the route module to mount
		 * @return this builder for chaining
		 */
		public Builder mount(Consumer<Builder> module) {
			module.accept(this);
			return this;
		}

		/**
		 * Builds the final immutable WebSocket router.
		 *
		 * @return the built router
		 */
		public WsRouter build() {
			return routerFactory.apply(new ArrayList<>(routes));
		}

		/**
		 * Default implementation of {@link WsLifecycleBuilder}. Collects callbacks and
		 * creates a {@link WebSocketHandler}.
		 */
		private static class DefaultWsLifecycleBuilder implements WsLifecycleBuilder {

			private Consumer<WebSocketContext> connectHandler;
			private Consumer<String> messageHandler;
			private Runnable closeHandler;

			@Override
			public WsLifecycleBuilder onConnect(Consumer<WebSocketContext> handler) {
				this.connectHandler = handler;
				return this;
			}

			@Override
			public WsLifecycleBuilder onMessage(Consumer<String> handler) {
				this.messageHandler = handler;
				return this;
			}

			@Override
			public WsLifecycleBuilder onClose(Runnable handler) {
				this.closeHandler = handler;
				return this;
			}

			WebSocketHandler build() {
				return ctx -> {
					if (connectHandler != null) {
						connectHandler.accept(ctx);
					}
					if (messageHandler != null) {
						ctx.onMessage(messageHandler);
					}
					if (closeHandler != null) {
						ctx.onClose(closeHandler);
					}
				};
			}
		}
	}
}
