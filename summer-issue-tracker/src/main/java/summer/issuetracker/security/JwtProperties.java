package summer.issuetracker.security;

import summer.core.config.ConfigurationProperties;

@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(String secret, long expirationMs) {
}
