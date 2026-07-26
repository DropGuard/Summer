package com.github.dropguard.summer.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that a component or bean method should only be registered when a
 * bean of the specified type exists in the container. If the condition is not
 * met, the component is silently skipped -- no error is thrown.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ConditionalOnBean {
	/** The bean type that must exist for this component to be registered. */
	Class<?> value();
}
