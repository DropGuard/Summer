package com.github.dropguard.summer.core.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class or record as a configuration properties holder.
 * 
 * <p>
 * The annotated type will be bound to a section of the YAML configuration file.
 * The {@link #prefix()} specifies which section to bind.
 * </p>
 * 
 * <p>
 * Example:
 * </p>
 * 
 * <pre>{@code
 * // YAML:
 * // jwt:
 * // secret: my-secret
 * // expiration: 3600000
 * 
 * @ConfigurationProperties(prefix = "jwt")
 * public record JwtProperties(String secret, long expiration) {
 * }
 * }</pre>
 * 
 * <p>
 * If no prefix is specified, the entire YAML file is bound to the type.
 * </p>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ConfigurationProperties {

	/**
	 * The prefix of the properties to bind. If empty, the entire configuration file
	 * is bound.
	 */
	String prefix() default "";
}
