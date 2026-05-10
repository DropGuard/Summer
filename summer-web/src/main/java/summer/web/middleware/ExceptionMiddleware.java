package summer.web.middleware;

import summer.web.Handler;

/**
 * Global exception middleware that handles exceptions during request
 * processing.
 */
public class ExceptionMiddleware implements Middleware {
	@Override
	public Handler apply(Handler handler) {
		return ctx -> {
			try {
				return handler.handle(ctx);
			} catch (Exception e) {
				ctx.response().error(e);
				System.err.println("Request failed: " + ctx.request().getPath());
				e.printStackTrace();
				return null;
			}
		};
	}
}