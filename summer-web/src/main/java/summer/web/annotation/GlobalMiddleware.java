package summer.web.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import summer.core.Component;

/**
 * Marks a Middleware as a global interceptor that will be automatically applied
 * to ALL requests (including 404s and static files) before routing.
 * 
 * Note: This annotation is meta-annotated with @Component, so the class will
 * automatically be managed by the DI container.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Component
public @interface GlobalMiddleware {
}
