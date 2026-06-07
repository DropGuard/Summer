package summer.web;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * HTTP request router with integrated builder.
 *
 * <p>
 * A router maps incoming HTTP requests to their corresponding handlers based on
 * method and path. This interface is <strong>immutable by design</strong> --it
 * exposes only the {@link #route(HttpContext)} method for dispatching requests.
 * Route registration is done via the {@link Builder} inner class.
 * </p>
 *
 * <h2>Building Routes</h2>
 *
 * <pre>{@code
 * HttpRouter router = new HttpRouter.Builder(RadixTreeHttpRouter::new).mount(UserRoutes::api).mount(OrderRoutes::api)
 * 		.build();
 *
 * // At runtime, only route() is available
 * Object result = router.route(httpContext);
 * }</pre>
 */
public interface HttpRouter {

	/**
	 * Routes an incoming request to the appropriate handler.
	 *
	 * @param ctx
	 *            the web context containing the request
	 * @return the handler result, or null if no route matches
	 */
	Object route(HttpContext ctx);

	/**
	 * Builder for HTTP routers. Provides a fluent DSL for defining routes.
	 *
	 * <p>
	 * Collects route definitions and builds an immutable router on
	 * {@link #build()}. The router implementation is determined by the factory
	 * function passed to the constructor.
	 * </p>
	 */
	class Builder {

		/**
		 * A route entry containing method, path pattern, and handler. Used to transfer
		 * route definitions to router engine factories.
		 */
		public record Route(HttpMethod method, String path, Handler handler) {
		}

		private final Function<List<Route>, HttpRouter> routerFactory;
		private final List<Route> routes = new ArrayList<>();
		private final List<Middleware> globalMiddlewares = new ArrayList<>();
		private final Deque<String> basePathStack = new ArrayDeque<>();
		private final Deque<List<Middleware>> middlewareStack = new ArrayDeque<>();

		/**
		 * Creates a new Builder with the specified router factory.
		 *
		 * @param routerFactory
		 *            a function that creates the router implementation from the route
		 *            list
		 */
		public Builder(Function<List<Route>, HttpRouter> routerFactory) {
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
			this(registry.httpFactory(type));
		}

		/**
		 * Registers a GET handler.
		 *
		 * @param path
		 *            the path pattern
		 * @param handler
		 *            the request handler
		 * @return this builder for chaining
		 */
		public Builder get(String path, Handler handler) {
			return route(HttpMethod.GET, path, handler);
		}

		/**
		 * Registers a POST handler.
		 *
		 * @param path
		 *            the path pattern
		 * @param handler
		 *            the request handler
		 * @return this builder for chaining
		 */
		public Builder post(String path, Handler handler) {
			return route(HttpMethod.POST, path, handler);
		}

		/**
		 * Registers a PUT handler.
		 *
		 * @param path
		 *            the path pattern
		 * @param handler
		 *            the request handler
		 * @return this builder for chaining
		 */
		public Builder put(String path, Handler handler) {
			return route(HttpMethod.PUT, path, handler);
		}

		/**
		 * Registers a DELETE handler.
		 *
		 * @param path
		 *            the path pattern
		 * @param handler
		 *            the request handler
		 * @return this builder for chaining
		 */
		public Builder delete(String path, Handler handler) {
			return route(HttpMethod.DELETE, path, handler);
		}

		private Builder route(HttpMethod method, String path, Handler handler) {
			String currentBase = basePathStack.peekLast();
			String fullPath = currentBase != null ? PathUtils.combinePaths(currentBase, path) : path;

			Handler wrappedHandler = handler;
			List<Middleware> currentMiddlewares = middlewareStack.peekLast();
			if (currentMiddlewares != null) {
				for (int i = currentMiddlewares.size() - 1; i >= 0; i--) {
					wrappedHandler = currentMiddlewares.get(i).apply(wrappedHandler);
				}
			}
			routes.add(new Route(method, fullPath, wrappedHandler));
			return this;
		}

		/**
		 * Adds middleware to the current scope. Middleware wraps handlers and adds
		 * cross-cutting concerns (authentication, logging, etc.).
		 *
		 * <p>
		 * When used inside a {@link #group}, the middleware only applies to routes
		 * within that group. When used at the top level, it applies to all subsequent
		 * routes.
		 * </p>
		 *
		 * @param middleware
		 *            the middleware to add
		 * @return this builder for chaining
		 */
		public Builder use(Middleware middleware) {
			List<Middleware> currentMiddlewares = middlewareStack.peekLast();
			if (currentMiddlewares != null) {
				currentMiddlewares.add(middleware);
			} else {
				globalMiddlewares.add(middleware);
			}
			return this;
		}

		/**
		 * Creates a route group under the given base path. All routes defined within
		 * the group callback share the base path prefix and can have shared middleware.
		 *
		 * <p>
		 * Example:
		 * </p>
		 *
		 * <pre>{@code
		 * router.group("/api/v1", v1 -> {
		 * 	v1.use(authMiddleware);
		 * 	v1.get("/users", UserController::list);
		 * 	v1.get("/users/{id}", UserController::getById);
		 * });
		 * }</pre>
		 *
		 * @param basePath
		 *            the base path prefix for all routes in this group
		 * @param groupConfig
		 *            callback to configure the group
		 * @return this builder for chaining
		 */
		public Builder group(String basePath, Consumer<Builder> groupConfig) {
			String currentBase = basePathStack.peekLast();
			String newBase = currentBase != null ? PathUtils.combinePaths(currentBase, basePath) : basePath;

			List<Middleware> currentMiddlewares = middlewareStack.peekLast();
			List<Middleware> groupMiddlewares = currentMiddlewares != null
					? new ArrayList<>(currentMiddlewares)
					: new ArrayList<>();

			basePathStack.addLast(newBase);
			middlewareStack.addLast(groupMiddlewares);

			groupConfig.accept(this);

			basePathStack.removeLast();
			middlewareStack.removeLast();

			return this;
		}

		/**
		 * Mounts a route module. The module callback receives this builder and
		 * registers routes on it.
		 *
		 * <p>
		 * This is the primary mechanism for modular route composition:
		 * </p>
		 *
		 * <pre>{@code
		 * // Define a module
		 * public class UserRoutes {
		 * 	public static void api(HttpRouter.Builder router) {
		 * 		router.group("/api/users", group -> {
		 * 			group.get("", UserController::list);
		 * 			group.get("/{id}", UserController::getById);
		 * 		});
		 * 	}
		 * }
		 *
		 * // Mount it
		 * router.mount(UserRoutes::api);
		 * }</pre>
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
		 * Builds the final immutable router from all registered definitions.
		 *
		 * @return the built router
		 */
		public HttpRouter build() {
			List<Route> resolved = new ArrayList<>(routes.size());
			for (Route route : routes) {
				Handler handler = route.handler();
				for (int i = globalMiddlewares.size() - 1; i >= 0; i--) {
					handler = globalMiddlewares.get(i).apply(handler);
				}
				resolved.add(new Route(route.method(), route.path(), handler));
			}
			return routerFactory.apply(resolved);
		}
	}
}
