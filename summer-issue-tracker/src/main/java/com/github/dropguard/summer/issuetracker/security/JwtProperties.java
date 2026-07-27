package com.github.dropguard.summer.issuetracker.security;

import com.github.dropguard.summer.core.config.ConfigMapping;

@ConfigMapping(prefix = "jwt")
public interface JwtProperties {

    String secret();

    long expirationMs();
}
