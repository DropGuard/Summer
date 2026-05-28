package summer.aop;

import java.lang.annotation.*;

/**
 * Declares which annotations a {@link MethodInterceptor} targets. Used by the
 * AOT compiler to determine at compile time which beans need proxy wrapping,
 * without runtime reflection.
 *
 * <p>
 * Example usage:
 * 
 * <pre>
 * {@literal @}Intercepts(annotations = Transactional.class)
 * public class TxInterceptor implements MethodInterceptor { ... }
 * </pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Intercepts {
	/**
	 * The method-level annotations whose presence on a bean triggers this
	 * interceptor.
	 */
	Class<? extends Annotation>[] annotations() default {};
}
