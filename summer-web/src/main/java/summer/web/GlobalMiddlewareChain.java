package summer.web;

import java.util.List;

/**
 * Immutable chain of global middlewares to be applied to all HTTP requests.
 * This is the read-only structure produced at application startup.
 */
public record GlobalMiddlewareChain(List<Class<? extends Middleware>> middlewares) {
	public GlobalMiddlewareChain {
		middlewares = List.copyOf(middlewares);
	}
}
