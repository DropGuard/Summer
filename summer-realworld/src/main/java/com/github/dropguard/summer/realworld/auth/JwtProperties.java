package com.github.dropguard.summer.realworld.auth;

import com.github.dropguard.summer.core.config.ConfigMapping;

/**
 * JWT configuration properties bound from {@code application.yml}.
 *
 * <pre>{@code
 * jwt:
 *   secret: your-secret-key
 * }</pre>
 */
@ConfigMapping(prefix = "jwt")
public interface JwtProperties {

    String secret();
}
