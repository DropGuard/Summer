package summer.web.middleware;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import summer.web.Handler;
import summer.web.Middleware;


/**
 * Logging middleware that logs request and response details.
 */

public class LoggingMiddleware implements Middleware {
	private static final Logger log = LoggerFactory.getLogger(LoggingMiddleware.class);

	@Override
	public Handler apply(Handler handler) {
		return ctx -> {
			long startTime = System.currentTimeMillis();

			log.info("Processing request: {} {}", ctx.method(), ctx.path());

			try {
				handler.handle(ctx);
				long duration = System.currentTimeMillis() - startTime;
				log.info("Completed response: {} in {}ms", ctx.statusCode(), duration);
			} catch (Exception e) {
				long duration = System.currentTimeMillis() - startTime;
				log.error("Failed to process request: {} ({}ms)", e.getMessage(), duration);
				throw e;
			}
		};
	}
}
