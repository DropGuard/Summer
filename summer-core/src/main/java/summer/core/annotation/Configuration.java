package summer.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a configuration source for the Summer framework.
 * Configuration classes can contain methods annotated with @Produces
 * to provide third-party or complex beans to the IoC container.
 * 
 * This annotation is used at compile-time by the Summer APT processor.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface Configuration {
}
