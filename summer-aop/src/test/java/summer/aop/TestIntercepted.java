package summer.aop;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Test-purpose interceptor binding for verifying AOP behavior.
 */
@InterceptorBinding
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface TestIntercepted {
}
