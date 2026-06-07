package summer.data.redis.config;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import summer.core.annotation.Bean;
import summer.core.annotation.Configuration;
import summer.data.redis.SummerRedisTemplate;
import summer.data.redis.codec.JsonRedisCodec;

@Configuration
public class RedisAutoConfiguration {

	@Bean
	public RedisProperties redisProperties() {
		// Fallback to System property to allow testcontainers to override the URI
		// dynamically
		String uri = System.getProperty("summer.redis.uri");
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

	@Bean
	public StatefulRedisConnection<String, Object> redisConnection(RedisClient client) {
		return client.connect(new JsonRedisCodec());
	}

	@Bean
	public RedisCommands<String, Object> redisCommands(StatefulRedisConnection<String, Object> connection) {
		return connection.sync();
	}

	@Bean
	public SummerRedisTemplate summerRedisTemplate(RedisCommands<String, Object> commands) {
		return new SummerRedisTemplate(commands);
	}
}
