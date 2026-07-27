package com.github.dropguard.summer.web;

/**
 * Represents a middleware that wraps a handler to add cross-cutting concerns.
 *
 * <p>This is the handler-chain interception mechanism, similar to Gin's middleware model. For
 * method-level AOP, use {@code @InterceptorBinding} instead.
 */
@FunctionalInterface
public interface Middleware {
    Handler apply(Handler handler);
}
