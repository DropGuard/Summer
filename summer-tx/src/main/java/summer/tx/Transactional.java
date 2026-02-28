package summer.tx;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as transactional. Only methods in classes that implement interfaces
 * will be intercepted using JDK dynamic proxies.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Transactional {
    TransactionPropagation propagation() default TransactionPropagation.REQUIRED;
}