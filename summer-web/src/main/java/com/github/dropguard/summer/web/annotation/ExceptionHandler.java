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
 *
 * <p><strong>Contract.</strong> Handlers are global: they apply to every route in the application,
 * regardless of which archive declares them. Matching is most-specific-first — the exact exception
 * class wins, otherwise the nearest superclass up the hierarchy. Registering two handlers for the
 * same exception class fails startup with a conflict error; distinct subclasses are fine.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ExceptionHandler {
    Class<? extends Throwable> value();
}
