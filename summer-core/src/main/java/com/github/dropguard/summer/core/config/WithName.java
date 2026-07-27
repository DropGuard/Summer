package com.github.dropguard.summer.core.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Overrides the configuration key for a {@link ConfigMapping} interface method.
 *
 * <p>By default a method's key is derived from its name via the kebab-case mapping
 * (e.g.&nbsp;{@code maxConn()} &rarr; {@code max-conn}). {@code @WithName} decouples the Java
 * identifier (which must be a valid identifier) from the external key (which may follow a different
 * naming convention) — the Go {@code json:"x"} equivalent. It <em>complements</em>, never replaces,
 * the default mapping: YAML stays kebab-case and untouched when {@code @WithName} is absent.
 *
 * <pre>{@code
 * @ConfigMapping(prefix = "server")
 * public interface ServerConfig {
 * 	@WithName("max-conn")
 * 	int maxConn();
 * }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface WithName {

    /**
     * The configuration key this method binds to, overriding the default derived from the method
     * name.
     */
    String value();
}
