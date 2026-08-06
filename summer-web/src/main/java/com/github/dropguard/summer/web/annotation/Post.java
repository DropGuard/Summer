package com.github.dropguard.summer.web.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks a method as handling HTTP POST requests. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Post {
    /**
     * The route path relative to the controller's base path ({@code @RestController.value()}).
     * Empty (the default) means "use the base path itself" — the method handles the controller's
     * own base path; a non-empty value is appended to the base path (or stands alone when the
     * controller declares no base path).
     */
    String value() default "";
}
