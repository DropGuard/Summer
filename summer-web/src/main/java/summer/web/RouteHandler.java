package summer.web;

/**
 * A functional interface for high-performance route handling.
 * This is used to replace reflection with direct method calls using LambdaMetafactory.
 */
@FunctionalInterface
public interface RouteHandler {
    /**
     * Executes the controller method.
     * @param instance The controller bean instance.
     * @param request The current HTTP request.
     * @return The result of the method execution.
     * @throws Throwable Any exception thrown by the method.
     */
    Object handle(Object instance, Request request) throws Throwable;
}
