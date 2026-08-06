package com.github.dropguard.summer.web.annotation;

import com.github.dropguard.summer.core.Component;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a REST controller that handles HTTP requests.
 *
 * <p>{@link #value()} sets an optional base path prefix for every route declared in the class: a
 * method-level route {@code @Get("/x")} becomes {@code basePath + "/x"}, and a method-level route
 * with the default empty value ({@code @Get}) maps to the base path itself. When the controller
 * declares no value, method routes stand alone. Paths are normalized via {@code PathUtils} (leading
 * slash added, slashes collapsed, no trailing slash).
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Component
public @interface RestController {
    /** Base path prefix for all routes in this controller. Empty (the default) means no prefix. */
    String value() default "";
}
