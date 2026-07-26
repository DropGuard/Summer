package com.github.dropguard.summer.data.redis.config;

import com.github.dropguard.summer.core.annotation.Bean;
import com.github.dropguard.summer.core.annotation.Configuration;
import com.github.dropguard.summer.data.redis.SummerRedisTemplate;
import com.github.dropguard.summer.data.redis.codec.JsonRedisCodec;
import io.lettuce.core.RedisClient;

@Configuration
public class RedisAutoConfiguration {

	@Bean
	public RedisProperties redisProperties() {
		// Fallback to System property to allow testcontainers to override the URI
		// dynamically
		String uri = System.getProperty("com.github.dropguard.summer.redis.uri");
		if (uri == null) {
			// Placeholder: Summer framework needs a unified Environment property resolver,
			// for now we default to localhost.
			uri = "redis://localhost:6379";
		}
		return new RedisProperties(uri);
	}

	@Bean
	public RedisClient redisClient(RedisProperties properties) {
		return RedisClient.create(properties.uri());
	}

	/**
	 * Binds the template to the {@link RedisClient}. The connection is opened
	 * lazily on the first command, so building this bean never requires a reachable
	 * Redis server — a context can be assembled (and the template mocked) in an
	 * environment without Redis, mirroring Quarkus' Redis client.
	 *
	 * <p>
	 * The previous wiring exposed a {@code StatefulRedisConnection} and
	 * {@code RedisCommands} bean and connected eagerly at startup; that forced a
	 * live Redis even for tests that never touch it. The template's lazy path
	 * removes that constraint.
	 * </p>
	 */
	@Bean
	public SummerRedisTemplate summerRedisTemplate(RedisClient redisClient) {
		return new SummerRedisTemplate(redisClient, new JsonRedisCodec());
	}
}
