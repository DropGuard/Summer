package com.github.dropguard.summer.data.redis;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dropguard.summer.core.json.SummerObjectMapper;
import com.github.dropguard.summer.data.redis.codec.JsonRedisCodec;
import io.lettuce.core.RedisClient;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;

/**
 * Type-safe Redis operations with explicit type passing, built on Lettuce.
 *
 * <h3>Two construction modes</h3>
 * <ul>
 * <li><b>Lazy (preferred)</b> — bound to a {@link RedisClient} and a
 * {@link JsonRedisCodec}. The network connection is opened on the first
 * command, never at construction time, so a context can be assembled in an
 * environment without a reachable Redis (unit tests, offline builds).
 * Serialization is performed by the codec, which owns the single
 * {@link ObjectMapper} shared with this template.</li>
 * <li><b>Eager</b> — wrapped around an already-resolved {@link RedisCommands}
 * handle (e.g. injected directly, or for tests). The connection and its codec
 * are owned by the caller; this template is a thin, serialization-agnostic
 * facade.</li>
 * </ul>
 *
 * <h3>Serialization contract</h3> Every stored value is JSON. A missing key
 * reads back as {@code null}; a {@code
 * null} value is never written — remove a key with {@link #delete(String)}
 * instead.
 *
 * <h3>Lifecycle</h3> In lazy mode the template is {@link AutoCloseable}; when
 * the enclosing framework container closes, the underlying connection and
 * client are released. In eager mode the caller owns the resources and this
 * template's {@link #close()} is a no-op.
 *
 * @see JsonRedisCodec
 * @see SummerObjectMapper
 */
public class SummerRedisTemplate implements AutoCloseable {

	private final ObjectMapper objectMapper;
	// Eager mode: a pre-built commands handle (caller-owned connection/codec).
	private final RedisCommands<String, Object> eagerCommands; // null in lazy mode
	// Lazy mode: the client is held and connected on first command.
	private final RedisClient client;
	private final JsonRedisCodec codec;
	private volatile StatefulRedisConnection<String, Object> connection;
	private volatile boolean closed = false;

	/**
	 * Wraps an already-resolved commands handle. Serialization is the caller's
	 * responsibility (the connection's codec decides it); this template uses a
	 * standalone mapper only for in-memory type conversion.
	 *
	 * @param commands
	 *            the Redis commands handle
	 */
	public SummerRedisTemplate(RedisCommands<String, Object> commands) {
		this(commands, SummerObjectMapper.create());
	}

	/**
	 * Wraps an already-resolved commands handle with an explicit mapper used for
	 * in-memory type conversion.
	 *
	 * @param commands
	 *            the Redis commands handle
	 * @param objectMapper
	 *            mapper for converting decoded values to target types
	 */
	public SummerRedisTemplate(RedisCommands<String, Object> commands, ObjectMapper objectMapper) {
		this.eagerCommands = commands;
		this.objectMapper = objectMapper;
		this.client = null;
		this.codec = null;
	}

	/**
	 * Binds to a {@link RedisClient} and {@link JsonRedisCodec}. The connection is
	 * opened lazily on the first command, so construction never requires a
	 * reachable Redis server. The template shares the codec's {@link ObjectMapper}
	 * as the single source of truth for (de)serialization.
	 *
	 * @param client
	 *            the Lettuce Redis client
	 * @param codec
	 *            the codec used to (de)serialize values
	 */
	public SummerRedisTemplate(RedisClient client, JsonRedisCodec codec) {
		this.client = client;
		this.codec = codec;
		this.objectMapper = codec.mapper();
		this.eagerCommands = null;
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
	 * Reads a value and deserializes it to the given type.
	 *
	 * @param key
	 *            the Redis key
	 * @param type
	 *            the target class
	 * @param <T>
	 *            the target type
	 * @return the value, or {@code null} if the key does not exist
	 */
	public <T> T get(String key, Class<T> type) {
		Object value = commands().get(key);
		if (value == null) {
			return null;
		}
		return objectMapper.convertValue(value, type);
	}

	/**
	 * Reads a value and deserializes it to the given generic type.
	 *
	 * @param key
	 *            the Redis key
	 * @param typeRef
	 *            the target generic type
	 * @param <T>
	 *            the target type
	 * @return the value, or {@code null} if the key does not exist
	 */
	public <T> T get(String key, TypeReference<T> typeRef) {
		Object value = commands().get(key);
		if (value == null) {
			return null;
		}
		return objectMapper.convertValue(value, typeRef);
	}

	/** Reads a value without conversion. {@code null} if the key is absent. */
	public Object getRaw(String key) {
		return commands().get(key);
	}

	/**
	 * Stores a value as JSON.
	 *
	 * @param key
	 *            the Redis key
	 * @param value
	 *            the value to store (must not be {@code null})
	 * @throws IllegalArgumentException
	 *             if {@code value} is {@code null}
	 */
	public void set(String key, Object value) {
		requireNonNullValue(value);
		commands().set(key, value);
	}

	/**
	 * Stores a value as JSON with a time-to-live.
	 *
	 * @param key
	 *            the Redis key
	 * @param value
	 *            the value to store (must not be {@code null})
	 * @param ttl
	 *            the time-to-live; honored at millisecond precision
	 * @throws IllegalArgumentException
	 *             if {@code value} is {@code null}
	 */
	public void set(String key, Object value, java.time.Duration ttl) {
		requireNonNullValue(value);
		commands().set(key, value, SetArgs.Builder.px(ttl.toMillis()));
	}

	/**
	 * Removes a key.
	 *
	 * @param key
	 *            the Redis key
	 * @return {@code true} if the key existed and was removed
	 */
	public boolean delete(String key) {
		return commands().del(key) > 0;
	}

	/**
	 * Checks whether a key exists.
	 *
	 * @param key
	 *            the Redis key
	 * @return {@code true} if the key exists
	 */
	public boolean exists(String key) {
		return commands().exists(key) > 0;
	}

	/**
	 * Sets the time-to-live of an existing key, at millisecond precision.
	 *
	 * @param key
	 *            the Redis key
	 * @param ttl
	 *            the new time-to-live
	 * @return {@code true} if the timeout was set (i.e. the key exists)
	 */
	public boolean expire(String key, java.time.Duration ttl) {
		return Boolean.TRUE.equals(commands().pexpire(key, ttl.toMillis()));
	}

	/**
	 * Advanced operations escape hatch. Returns the underlying sync commands for
	 * callers needing commands beyond the typed single-key API (e.g. sorted-set
	 * fan-out). The returned handle shares this template's codec, so values are
	 * serialized with the same JSON contract.
	 *
	 * @return the sync Redis commands
	 */
	public RedisCommands<String, Object> getCommands() {
		return commands();
	}

	/**
	 * Executes a Lua script atomically on the server. Exposed for operations that
	 * need compare-and-act semantics (e.g. a flash-sale decrement) beyond the typed
	 * single-key API.
	 *
	 * @param <T>
	 *            the script return type
	 * @param script
	 *            the Lua source
	 * @param type
	 *            the expected output type
	 * @param keys
	 *            keys referenced by {@code KEYS[n]}
	 * @param args
	 *            additional script arguments
	 * @return the script result, or {@code null} if absent
	 */
	public <T> T eval(String script, ScriptOutputType type, String[] keys, String... args) {
		return commands().eval(script, type, keys, args);
	}

	/** The {@link ObjectMapper} used for value (de)serialization. */
	public ObjectMapper getObjectMapper() {
		return objectMapper;
	}

	private static void requireNonNullValue(Object value) {
		if (value == null) {
			throw new IllegalArgumentException("Cannot store null in Redis; remove a key with delete(key) instead.");
		}
	}

	/**
	 * Releases the underlying connection and client in lazy mode. In eager mode the
	 * caller owns the resources, so this is a no-op. Safe to call multiple times.
	 */
	@Override
	public void close() {
		if (closed) {
			return;
		}
		synchronized (this) {
			if (closed) {
				return;
			}
			StatefulRedisConnection<String, Object> conn = connection;
			if (conn != null) {
				conn.close();
				connection = null;
			}
			if (client != null) {
				client.shutdown();
			}
			closed = true;
		}
	}
}
