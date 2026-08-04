package com.github.dropguard.summer.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the sort order of a bean within a {@code List<T>} injection.
 *
 * <p>Beans are sorted in ascending order (lower values come first); beans without {@code @Order}
 * default to {@code 0}. Beans with the same order value fall back to registration order.
 *
 * <pre>{@code
 * @Component
 * @Order(1)
 * public class LoggingMiddleware implements Middleware { ... }
 *
 * @Component
 * @Order(2)
 * public class AuthMiddleware implements Middleware { ... }
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Order {

    /** Lower values have higher priority. Default is {@code 0}. */
    int value() default 0;
}
