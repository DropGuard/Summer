package com.github.dropguard.summer.web.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a {@code Middleware} bean as globally applied to every request.
 *
 * <p>A {@code @GlobalMiddleware} bean is automatically collected by the web server runner and
 * wrapped around the root router, so it intercepts all incoming requests (e.g. CORS,
 * authentication, logging, metrics). Order across global middleware is explicit, not
 * priority-based: middleware declared via {@code SummerApplication.apply(...)} are applied first,
 * in declaration order, followed by {@code @GlobalMiddleware}-annotated beans in container
 * registration order.
 *
 * <p>Middleware beans <em>without</em> this annotation are not wired globally; they are intended
 * for scoped use via the programmatic router DSL ({@code HttpRouter.Builder.use(...)} / {@code
 * group(...)}), exactly like Quarkus' {@code @RouteFilter} only applies when annotated.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface GlobalMiddleware {}
