package com.github.dropguard.summer.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that a component or bean method should only be registered when a bean of the specified
 * type exists in the container. If the condition is not met, the component is silently skipped --
 * no error is thrown.
 *
 * <p><strong>Visibility is the whole container.</strong> The required type may live in any archive
 * of the universe — application jar, framework module, or test slice alike. Conditions follow the
 * same single visibility model as bean injection: one container, one scope. This is documented,
 * tested behavior — narrowing it in a later release would be a breaking change under semver, not a
 * fix.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ConditionalOnBean {
    /** The bean type that must exist for this component to be registered. */
    Class<?> value();
}
