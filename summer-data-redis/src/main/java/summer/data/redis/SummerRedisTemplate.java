package summer.data.redis;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.api.sync.RedisCommands;
import summer.core.json.SummerObjectMapper;

/**
 * High-level Redis template that provides type-safe operations with explicit
 * type passing.
 *
 * <p>
 * This template wraps Lettuce's {@link RedisCommands} and uses Jackson for
 * serialization/deserialization. It supports both {@link Class} and
 * {@link TypeReference} for explicit type specification.
 * </p>
 *
 * <h3>Usage Examples</h3>
 *
 * <pre>
 * // 1. Simple type
 * UserCacheDTO user = redisTemplate.get("user:1", UserCacheDTO.class);
 *
 * // 2. Generic type with TypeReference
 * List<String> roles = redisTemplate.get("user:1:roles", new TypeReference<List<String>>() {
 * });
 *
 * // 3. Store value
 * redisTemplate.set("user:1", new UserCacheDTO(1L, "gemini", List.of("admin")));
 *
 * // 4. Store with expiration
 * redisTemplate.set("user:1", new UserCacheDTO(1L, "gemini", List.of("admin")), Duration.ofHours(1));
 * </pre>
 *
 * @see SummerObjectMapper
 */
public class SummerRedisTemplate {

	private final RedisCommands<String, Object> commands;
	private final ObjectMapper objectMapper;

	/**
	 * Creates a new SummerRedisTemplate with the given Redis commands and default
	 * ObjectMapper.
	 *
	 * @param commands
	 *            the Redis commands
	 */
	public SummerRedisTemplate(RedisCommands<String, Object> commands) {
		this(commands, SummerObjectMapper.create());
	}

	/**
	 * Creates a new SummerRedisTemplate with the given Redis commands and custom
	 * ObjectMapper.
	 *
	 * @param commands
	 *            the Redis commands
	 * @param objectMapper
	 *            the ObjectMapper to use for serialization/deserialization
	 */
	public SummerRedisTemplate(RedisCommands<String, Object> commands, ObjectMapper objectMapper) {
		this.commands = commands;
		this.objectMapper = objectMapper;
	}

	/**
	 * Gets a value from Redis and deserializes it to the specified type.
	 *
	 * @param <T>
	 *            the target type
	 * @param key
	 *            the Redis key
	 * @param type
	 *            the target class
	 * @return the deserialized value, or null if the key does not exist
	 */
	public <T> T get(String key, Class<T> type) {
		Object value = commands.get(key);
		if (value == null) {
			return null;
		}
		return objectMapper.convertValue(value, type);
	}

	/**
	 * Gets a value from Redis and deserializes it to the specified generic type.
	 *
	 * @param <T>
	 *            the target type
	 * @param key
	 *            the Redis key
	 * @param typeRef
	 *            the TypeReference describing the target generic type
	 * @return the deserialized value, or null if the key does not exist
	 */
	public <T> T get(String key, TypeReference<T> typeRef) {
		Object value = commands.get(key);
		if (value == null) {
			return null;
		}
		return objectMapper.convertValue(value, typeRef);
	}

	/**
	 * Gets a raw value from Redis without deserialization.
	 *
	 * @param key
	 *            the Redis key
	 * @return the raw value, or null if the key does not exist
	 */
	public Object getRaw(String key) {
		return commands.get(key);
	}

	/**
	 * Sets a value in Redis.
	 *
	 * @param key
	 *            the Redis key
	 * @param value
	 *            the value to store
	 */
	public void set(String key, Object value) {
		commands.set(key, value);
	}

	/**
	 * Sets a value in Redis with expiration.
	 *
	 * @param key
	 *            the Redis key
	 * @param value
	 *            the value to store
	 * @param ttl
	 *            the time-to-live duration
	 */
	public void set(String key, Object value, java.time.Duration ttl) {
		commands.set(key, value);
		commands.expire(key, ttl.getSeconds());
	}

	/**
	 * Deletes a key from Redis.
	 *
	 * @param key
	 *            the Redis key
	 * @return true if the key was deleted, false if it did not exist
	 */
	public boolean delete(String key) {
		return commands.del(key) > 0;
	}

	/**
	 * Checks if a key exists in Redis.
	 *
	 * @param key
	 *            the Redis key
	 * @return true if the key exists, false otherwise
	 */
	public boolean exists(String key) {
		return commands.exists(key) > 0;
	}

	/**
	 * Sets the expiration time for a key.
	 *
	 * @param key
	 *            the Redis key
	 * @param ttl
	 *            the time-to-live duration
	 * @return true if the timeout was set, false if the key does not exist
	 */
	public boolean expire(String key, java.time.Duration ttl) {
		return commands.expire(key, ttl.getSeconds());
	}

	/**
	 * Gets the underlying Redis commands for advanced operations.
	 *
	 * @return the Redis commands
	 */
	public RedisCommands<String, Object> getCommands() {
		return commands;
	}

	/**
	 * Gets the ObjectMapper used for serialization/deserialization.
	 *
	 * @return the ObjectMapper
	 */
	public ObjectMapper getObjectMapper() {
		return objectMapper;
	}
}
