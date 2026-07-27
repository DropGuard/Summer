package com.github.dropguard.summer.web.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks a method as handling HTTP PUT requests. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Put {
    String value() default "";
}
