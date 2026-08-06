package com.github.dropguard.summer.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the sort order of a bean within a {@code List<T>} injection.
 *
 * <p>Beans are sorted in ascending order (lower values come first); beans without {@code @Order}
 * sort last. Beans with the same order value fall back to registration order.
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

    /**
     * Sort priority. Lower values come first.
     *
     * <p>The Java default {@code 0} applies only when {@code @Order} is present without an explicit
     * value (sorts first). A bean with <em>no</em> {@code @Order} annotation at all is not treated
     * as {@code 0} — it sorts last (see {@code BeanContainer.orderOf}, which returns {@code
     * Integer.MAX_VALUE} in that case).
     */
    int value() default 0;
}
