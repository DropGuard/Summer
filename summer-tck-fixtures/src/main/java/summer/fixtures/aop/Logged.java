package summer.fixtures.aop;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import summer.aop.InterceptorBinding;

/**
 * Test-purpose interceptor binding for verifying AOP behavior.
 *
 * <p>
 * Can be placed on a method (intercept that method only) or on a class
 * (intercept all methods).
 * </p>
 */
@InterceptorBinding
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Logged {
}
