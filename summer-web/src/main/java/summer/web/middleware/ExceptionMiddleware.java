package summer.web.middleware;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import summer.web.ExceptionRegistry;
import summer.web.Handler;
import summer.web.annotation.GlobalMiddleware;
import summer.web.exception.SummerWebException;

/**
 * Global exception middleware that handles exceptions during request processing
 * using the ExceptionRegistry.
 */
@GlobalMiddleware
public class ExceptionMiddleware implements Middleware {
	private static final Logger log = LoggerFactory.getLogger(ExceptionMiddleware.class);
	private final ExceptionRegistry registry;

	public ExceptionMiddleware(ExceptionRegistry registry) {
		this.registry = registry;
	}

	@Override
	public Handler apply(Handler handler) {
		return ctx -> {
			try {
				return handler.handle(ctx);
			} catch (Exception e) {
				Handler customHandler = registry.getHandler(e);
				if (customHandler != null) {
					try {
						ctx.request().setAttribute("last_exception", e);
						return customHandler.handle(ctx);
					} catch (Exception ex) {
						e = ex;
					}
				}

				if (e instanceof SummerWebException webEx) {
					ctx.status(webEx.statusCode());
				}
				ctx.error(e);
				log.error("Request failed: {}", ctx.request().getPath(), e);
				return null;
			}
		};
	}
}
