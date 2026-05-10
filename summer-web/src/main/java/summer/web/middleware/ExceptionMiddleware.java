package summer.web.middleware;

import summer.core.Component;
import summer.web.ExceptionRegistry;
import summer.web.Handler;

/**
 * Global exception middleware that handles exceptions during request
 * processing using the ExceptionRegistry.
 */
@Component
public class ExceptionMiddleware implements Middleware {
	private final ExceptionRegistry registry;

	public ExceptionMiddleware(ExceptionRegistry registry) {
		this.registry = registry;
	}

	@Override
	public Handler apply(Handler handler) {
		return ctx -> {
			try {
				return handler.handle(ctx);
			} catch (Throwable e) {
				Handler customHandler = registry.getHandler(e);
				if (customHandler != null) {
					try {
						// Store the exception for the fallback handler logic
						ctx.request().setAttribute("last_exception", e);
						return customHandler.handle(ctx);
					} catch (Throwable ex) {
						e = ex; // Fallback if custom handler fails
					}
				}

				ctx.response().error(e instanceof Exception ? (Exception)e : new RuntimeException(e));
				System.err.println("Request failed: " + ctx.request().getPath());
				e.printStackTrace();
				return null;
			}
		};
	}
}