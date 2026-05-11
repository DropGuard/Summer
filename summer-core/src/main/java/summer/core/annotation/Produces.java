package summer.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method within a @Configuration class as a producer of a bean.
 * The Summer APT processor will generate a Provider implementation for
 * each method annotated with @Produces.
 * 
 * This annotation is used at compile-time by the Summer APT processor.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.SOURCE)
public @interface Produces {
}
