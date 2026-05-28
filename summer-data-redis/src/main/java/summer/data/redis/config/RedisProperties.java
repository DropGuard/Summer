package summer.data.redis.config;

public record RedisProperties(String uri) {
	public RedisProperties {
		if (uri == null || uri.isBlank()) {
			uri = "redis://localhost:6379";
		}
	}
}
