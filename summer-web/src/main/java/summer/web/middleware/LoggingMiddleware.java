package summer.web.middleware;

import summer.web.Handler;

/**
 * Logging middleware that logs request and response details.
 */
public class LoggingMiddleware implements Middleware {
	@Override
	public Handler apply(Handler handler) {
		return ctx -> {
			long startTime = System.currentTimeMillis();

			System.out.println("Processing request: " + ctx.request().getMethod() + " " + ctx.request().getPath());

			try {
				Object result = handler.handle(ctx);
				long duration = System.currentTimeMillis() - startTime;
				System.out.println("Completed response: " + ctx.response().getStatusCode() + " in " + duration + "ms");
				return result;
			} catch (Exception e) {
				long duration = System.currentTimeMillis() - startTime;
				System.err.println("Failed to process request: " + e.getMessage() + " (" + duration + "ms)");
				throw e;
			}
		};
	}
}