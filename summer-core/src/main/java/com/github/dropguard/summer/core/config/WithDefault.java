package com.github.dropguard.summer.core.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a default value for a {@link ConfigMapping} interface method. The value is applied when
 * the property is <em>unbound</em> — i.e.&nbsp;the key is absent from the configuration map.
 *
 * <p>The {@link #value()} is always a {@code String}; it is parsed to the method's return type at
 * bind time.
 *
 * <pre>{@code
 * @ConfigMapping(prefix = "server")
 * public interface ServerConfig {
 * 	@WithDefault("8080")
 * 	Integer port();
 * 	@WithDefault("false")
 * 	Boolean ssl();
 * }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface WithDefault {

    /**
     * The default value, expressed as a string. Parsed to the method's return type (supports {@code
     * String}, {@code int}, {@code long}, {@code boolean}, {@code double}, {@code float}, {@code
     * short}, {@code byte} and their boxed counterparts, plus {@code enum}).
     */
    String value();
}
