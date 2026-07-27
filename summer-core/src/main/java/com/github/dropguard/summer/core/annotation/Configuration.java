package com.github.dropguard.summer.core.annotation;

import com.github.dropguard.summer.core.Component;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a configuration source for the Summer framework. Configuration classes can
 * contain methods annotated with @Bean to provide third-party or complex beans to the IoC
 * container.
 *
 * <p>Meta-annotated with {@link Component}, so configuration classes are automatically discovered
 * by component scanning.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Component
public @interface Configuration {}
