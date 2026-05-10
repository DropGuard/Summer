package summer.web;

/**
 * Logging middleware that logs request and response details.
 */
public class LoggingMiddleware implements Middleware {
	@Override
	public Handler apply(Handler handler) {
		return (request, response) -> {
			long startTime = System.currentTimeMillis();

			System.out.println("Processing request: " + request.getMethod() + " " + request.getPath());

			try {
				Object result = handler.handle(request, response);
				long duration = System.currentTimeMillis() - startTime;
				System.out.println("Completed response: " + response.getStatusCode() + " in " + duration + "ms");
				return result;
			} catch (Exception e) {
				long duration = System.currentTimeMillis() - startTime;
				System.err.println("Failed to process request: " + e.getMessage() + " (" + duration + "ms)");
				throw e;
			}
		};
	}
}