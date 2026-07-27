package com.github.dropguard.summer.core.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an interface as a configuration mapping. Quarkus-style: the interface's abstract methods
 * <em>are</em> the configuration keys (method name = key, with the default kebab-case mapping
 * already performed by {@link ConfigBinder#normalizeKeys}).
 *
 * <p>The annotated interface will be bound to a section of the YAML configuration file. The {@link
 * #prefix()} specifies which section to bind. Use {@link WithName} to override a method's key, and
 * {@link WithDefault} to supply a default when the key is absent.
 *
 * <p>Example:
 *
 * <pre>{@code
 * // YAML:
 * // jwt:
 * // secret: my-secret
 * // expiration-ms: 3600000
 *
 * @ConfigMapping(prefix = "jwt")
 * public interface JwtProperties {
 * 	String secret();
 * 	@WithDefault("3600000")
 * 	long expirationMs();
 * }
 * }</pre>
 *
 * <p>If no prefix is specified, the entire YAML file is bound to the interface.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ConfigMapping {

    /** The prefix of the properties to bind. If empty, the entire configuration file is bound. */
    String prefix() default "";
}
