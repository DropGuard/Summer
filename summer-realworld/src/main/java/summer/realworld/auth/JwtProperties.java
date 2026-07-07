package summer.realworld.auth;

import summer.core.config.ConfigurationProperties;

/**
 * JWT configuration properties bound from {@code application.yml}.
 *
 * <pre>{@code
 * jwt:
 *   secret: your-secret-key
 * }</pre>
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(String secret) {
}
