package com.github.dropguard.summer.issuetracker.security;

import com.github.dropguard.summer.core.config.ConfigurationProperties;

@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(String secret, long expirationMs) {
}
