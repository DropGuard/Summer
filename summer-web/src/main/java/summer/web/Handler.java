package summer.web;

/**
 * Represents a request handler that processes HTTP requests.
 */
@FunctionalInterface
public interface Handler {
	Object handle(Request request, Response response);
}