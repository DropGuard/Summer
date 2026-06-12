package summer.web;

/**
 * Represents a request handler that processes HTTP requests.
 *
 * <p>
 * Handlers are invoked by the router during request processing. The return
 * value of {@link #handle(HttpContext)} is <b>not</b> used by the framework --
 * controllers must write responses explicitly via {@link HttpContext} methods
 * (e.g., {@code ctx.ok(data)}, {@code ctx.json(status, data)}).
 * </p>
 *
 * <p>
 * This is by design: Summer uses a deferred write pattern where the response is
 * written to the context during request processing, then flushed to the network
 * by the IO layer.
 * </p>
 *
 * @see HttpContext
 */
@FunctionalInterface
public interface Handler {
	/**
	 * Handles an HTTP request. Controllers must write responses explicitly via
	 * {@link HttpContext} methods (e.g., {@code ctx.ok(data)}, {@code ctx.text(status, body)}).
	 */
	void handle(HttpContext ctx);
}
