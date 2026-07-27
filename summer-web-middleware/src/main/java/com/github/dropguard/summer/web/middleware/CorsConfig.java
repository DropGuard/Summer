package com.github.dropguard.summer.web.middleware;

import com.github.dropguard.summer.core.config.ConfigMapping;
import com.github.dropguard.summer.core.config.WithDefault;

/**
 * CORS configuration bound from {@code application.yml}.
 *
 * <p>Example YAML:
 *
 * <pre>{@code
 * cors:
 *   allowed-origins: "*"
 *   allowed-methods: "GET, POST, PUT, DELETE, OPTIONS"
 *   allowed-headers: "Content-Type, Authorization"
 *   max-age: 3600
 * }</pre>
 *
 * @param allowedOrigins the allowed origins (comma-separated or "*" for all)
 * @param allowedMethods the allowed HTTP methods
 * @param allowedHeaders the allowed request headers
 * @param maxAge the max age in seconds for preflight cache
 */
@ConfigMapping(prefix = "cors")
public interface CorsConfig {

    @WithDefault("*")
    String allowedOrigins();

    @WithDefault("GET, POST, PUT, DELETE, OPTIONS")
    String allowedMethods();

    @WithDefault("Content-Type, Authorization")
    String allowedHeaders();

    @WithDefault("3600")
    Integer maxAge();
}
