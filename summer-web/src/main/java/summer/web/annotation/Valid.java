package summer.web.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller method parameter for validation. When applied, the
 * framework requires a BodyValidator to be present and will execute validation
 * against the incoming request body. If validation fails, or if no
 * BodyValidator is configured in the application context, an exception will be
 * thrown.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface Valid {
}
