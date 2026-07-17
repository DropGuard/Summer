package summer.data.redis;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.RedisClient;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import summer.core.json.SummerObjectMapper;
import summer.data.redis.codec.JsonRedisCodec;

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
 * <h3>Connection model</h3>
 *
 * <p>
 * When constructed from a {@link RedisClient} (the framework's default wiring),
 * the connection is opened <em>lazily</em> — on the first command — not at
 * container startup. This mirrors Quarkus' Redis client, which defers network
 * I/O until first use, so a context can be built in an environment without a
 * running Redis (e.g. unit tests that mock the template) without failing.
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

	private final ObjectMapper objectMapper;
	// Eager path: a pre-built commands handle (e.g. injected directly, or for
	// tests).
	private volatile RedisCommands<String, Object> eagerCommands;
	// Lazy path: the client is held and connected on first command.
	private final RedisClient client;
	private final JsonRedisCodec codec;
	private volatile StatefulRedisConnection<String, Object> connection;

	/**
	 * Creates a new SummerRedisTemplate from an already-resolved commands handle.
	 *
	 * @param commands
	 *            the Redis commands
	 */
	public SummerRedisTemplate(RedisCommands<String, Object> commands) {
		this(commands, SummerObjectMapper.create());
	}

	/**
	 * Creates a new SummerRedisTemplate from an already-resolved commands handle
	 * with a custom ObjectMapper.
	 *
	 * @param commands
	 *            the Redis commands
	 * @param objectMapper
	 *            the ObjectMapper to use for serialization/deserialization
	 */
	public SummerRedisTemplate(RedisCommands<String, Object> commands, ObjectMapper objectMapper) {
		this.eagerCommands = commands;
		this.objectMapper = objectMapper;
		this.client = null;
		this.codec = null;
	}

	/**
	 * Creates a new SummerRedisTemplate bound to a {@link RedisClient}. The
	 * connection is opened lazily on the first command, so building the bean does
	 * not require a reachable Redis server.
	 *
	 * @param client
	 *            the Lettuce Redis client
	 * @param codec
	 *            the codec used to (de)serialize values
	 */
	public SummerRedisTemplate(RedisClient client, JsonRedisCodec codec) {
		this.client = client;
		this.codec = codec;
		this.objectMapper = SummerObjectMapper.create();
	}

	private RedisCommands<String, Object> commands() {
		if (eagerCommands != null) {
			return eagerCommands;
		}
		StatefulRedisConnection<String, Object> conn = connection;
		if (conn == null) {
			synchronized (this) {
				conn = connection;
				if (conn == null) {
					conn = client.connect(codec);
					connection = conn;
				}
			}
		}
		return conn.sync();
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
		Object value = commands().get(key);
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
		Object value = commands().get(key);
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
		return commands().get(key);
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
		commands().set(key, value);
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
		commands().setex(key, ttl.getSeconds(), value);
	}

	/**
	 * Deletes a key from Redis.
	 *
	 * @param key
	 *            the Redis key
	 * @return true if the key was deleted, false if it did not exist
	 */
	public boolean delete(String key) {
		return commands().del(key) > 0;
	}

	/**
	 * Checks if a key exists in Redis.
	 *
	 * @param key
	 *            the Redis key
	 * @return true if the key exists, false otherwise
	 */
	public boolean exists(String key) {
		return commands().exists(key) > 0;
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
		return commands().expire(key, ttl.getSeconds());
	}

	/**
	 * Gets the underlying Redis commands for advanced operations.
	 *
	 * @return the Redis commands
	 */
	public RedisCommands<String, Object> getCommands() {
		return commands();
	}

	/**
	 * Executes a Lua script with the supplied keys and arguments. Exposed for
	 * callers that need atomic, server-side execution (e.g. a compare-and-decr
	 * flash-sale loop) beyond the typed single-key operations above.
	 *
	 * @param <T>
	 *            the script return type
	 * @param script
	 *            the Lua source
	 * @param type
	 *            the expected output type
	 * @param keys
	 *            the Redis keys referenced by {@code KEYS[n]}
	 * @param args
	 *            additional script arguments
	 * @return the script result, or null if absent
	 */
	public <T> T eval(String script, ScriptOutputType type, String[] keys, String... args) {
		return commands().eval(script, type, keys, args);
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
