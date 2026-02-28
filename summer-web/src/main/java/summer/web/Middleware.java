package summer.web;

/**
 * Represents a middleware that wraps a handler to add cross-cutting concerns.
 */
@FunctionalInterface
public interface Middleware {
    Handler apply(Handler handler);
}