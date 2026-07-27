package com.github.dropguard.summer.web.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks a method as a global exception handler. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ExceptionHandler {
    Class<? extends Throwable> value();
}
