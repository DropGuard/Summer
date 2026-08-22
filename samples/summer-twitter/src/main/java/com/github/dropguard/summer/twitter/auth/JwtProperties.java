package com.github.dropguard.summer.twitter.auth;

import com.github.dropguard.summer.core.config.ConfigMapping;

@ConfigMapping(prefix = "jwt")
public interface JwtProperties {

    String secret();
}
