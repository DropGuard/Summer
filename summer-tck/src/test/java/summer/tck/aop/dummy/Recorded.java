package summer.tck.aop.dummy;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import summer.aop.InterceptorBinding;

/**
 * Test-purpose interceptor binding for verifying AOP behavior.
 */
@InterceptorBinding
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Recorded {
}
