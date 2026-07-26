package com.github.dropguard.summer.twitter.auth;

import com.github.dropguard.summer.core.config.ConfigurationProperties;

@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(String secret) {}