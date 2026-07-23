package summer.tck.negative.fixtures.aop.errors;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import summer.aop.InterceptorBinding;

/**
 * Local interceptor-binding marker for the negative AOP fixture. Defined here
 * (instead of reusing {@code @Logged}) so this module stays free of the
 * {@code summer-tck-fixtures} dependency and the broken bean remains reachable
 * ONLY through the narrow {@code @SummerTest(classes=...)} path.
 */
@InterceptorBinding
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface AopMarker {
}
