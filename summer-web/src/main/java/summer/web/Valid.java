package summer.web;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method parameter to be validated before method invocation.
 * The validation is performed using the validator factory configured in the application context.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface Valid {
    // Optional validation groups
    Class<?>[] groups() default {};
}
