package com.github.dropguard.summer.data.redis.config;

import com.github.dropguard.summer.core.annotation.Bean;
import com.github.dropguard.summer.core.annotation.Configuration;
import com.github.dropguard.summer.data.redis.SummerRedisTemplate;
import com.github.dropguard.summer.data.redis.codec.JsonRedisCodec;
import io.lettuce.core.RedisClient;

@Configuration
public class RedisAutoConfiguration {

    /**
     * Builds the Lettuce client from the bound {@link RedisProperties}. The URI comes exclusively
     * from {@code @ConfigMapping} (YAML + {@code ${VAR}} placeholders), externalized 12-factor
     * style — never a raw system property.
     */
    @Bean
    public RedisClient redisClient(RedisProperties properties) {
        return RedisClient.create(properties.uri());
    }

    /**
     * Binds the template to the {@link RedisClient}. The connection is opened lazily on the first
     * command, so building this bean never requires a reachable Redis server — a context can be
     * assembled (and the template mocked) in an environment without Redis, mirroring Quarkus' Redis
     * client.
     */
    @Bean
    public SummerRedisTemplate summerRedisTemplate(RedisClient redisClient) {
        return new SummerRedisTemplate(redisClient, new JsonRedisCodec());
    }
}
