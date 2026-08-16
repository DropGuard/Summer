package com.github.dropguard.summer.web.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as a global exception handler.
 *
 * <p>The registry matches the thrown exception's type hierarchy ({@link
 * com.github.dropguard.summer.web.ExceptionRegistry}). Handler methods may throw — runtime or
 * checked — and {@code Handler.handle} declares {@code throws Exception}, so both propagate
 * unwrapped to their matching handler (the Gin panic-recovery model in Java).
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ExceptionHandler {
    Class<? extends Throwable> value();
}
