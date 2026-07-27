package com.github.dropguard.summer.web.annotation;

import com.github.dropguard.summer.core.Component;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks a class as a REST controller that handles HTTP requests. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Component
public @interface RestController {
    String value() default "";
}
