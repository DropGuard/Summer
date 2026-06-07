package summer.web.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import summer.core.Component;

/**
 * Marks a middleware as global, applying it to all routes automatically.
 *
 * <p>
 * Global middlewares are collected at startup and applied to every HTTP request
 * before any route-specific middlewares.
 * </p>
 */
@Component
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface GlobalMiddleware {
}
