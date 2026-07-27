package com.github.dropguard.summer.data.redis.config;

import com.github.dropguard.summer.core.config.ConfigMapping;
import com.github.dropguard.summer.core.config.WithDefault;

/**
 * Quarkus-style config mapping for the Redis client URI. Bound from the {@code redis:} YAML
 * section; the URI is externalized 12-factor style via the {@code
 * ${COM_GITHUB_DROPGUARD_SUMMER_REDIS_URI}} placeholder, never a raw system property.
 */
@ConfigMapping(prefix = "redis")
public interface RedisProperties {

    @WithDefault("redis://localhost:6379")
    String uri();
}
