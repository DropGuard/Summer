package summer.web.middleware;

import summer.core.config.ConfigurationProperties;
import summer.core.config.DefaultValue;

/**
 * CORS configuration bound from {@code application.yml}.
 *
 * <p>
 * Example YAML:
 * </p>
 *
 * <pre>{@code
 * cors:
 *   allowed-origins: "*"
 *   allowed-methods: "GET, POST, PUT, DELETE, OPTIONS"
 *   allowed-headers: "Content-Type, Authorization"
 *   max-age: 3600
 * }</pre>
 *
 * @param allowedOrigins
 *            the allowed origins (comma-separated or "*" for all)
 * @param allowedMethods
 *            the allowed HTTP methods
 * @param allowedHeaders
 *            the allowed request headers
 * @param maxAge
 *            the max age in seconds for preflight cache
 */
@ConfigurationProperties(prefix = "cors")
public record CorsConfig(@DefaultValue("*") String allowedOrigins,
		@DefaultValue("GET, POST, PUT, DELETE, OPTIONS") String allowedMethods,
		@DefaultValue("Content-Type, Authorization") String allowedHeaders, @DefaultValue("3600") Integer maxAge) {
}
