package com.github.dropguard.summer.web.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Binds a method parameter to a URL query parameter.
 *
 * <p>Example:
 *
 * <pre>{@code
 * @Get("/search")
 * public String search(@QueryParam("q") String query, @QueryParam("page") int page) {
 * 	// /search?q=hello&page=1
 * }
 * }</pre>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface QueryParam {

    /** The query parameter name. */
    String value();
}
