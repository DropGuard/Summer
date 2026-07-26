package com.github.dropguard.summer.data.redis;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dropguard.summer.data.redis.codec.JsonRedisCodec;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pins the serialization architecture: in lazy mode the template and its codec
 * must share a single {@link ObjectMapper} instance, and a value must
 * round-trip through that one mapper. Two independent mappers would drift and
 * break round-trips the moment either is customized.
 */
public class RedisTemplateSerializationContractTest {

	public record Event(String name, int count, LocalDateTime at) {
	}

	@Test
	void lazyTemplateSharesCodecMapper() {
		JsonRedisCodec codec = new JsonRedisCodec();
		SummerRedisTemplate template = new SummerRedisTemplate(mock(RedisClient.class), codec);
		assertSame(codec.mapper(), template.getObjectMapper());
	}

	@Test
	void lazyTemplateUsesSharedMapperForRoundTrip() {
		JsonRedisCodec codec = new JsonRedisCodec();
		RedisClient client = mock(RedisClient.class);
		StatefulRedisConnection<String, Object> conn = mock(StatefulRedisConnection.class);
		RedisCommands<String, Object> commands = mock(RedisCommands.class);
		when(client.connect(codec)).thenReturn(conn);
		when(conn.sync()).thenReturn(commands);

		SummerRedisTemplate template = new SummerRedisTemplate(client, codec);

		Event original = new Event("launch", 7, LocalDateTime.of(2024, 5, 1, 9, 0));
		// Capture the value Lettuce would encode, then feed it back as a read.
		when(commands.set(eq("evt:1"), any())).thenReturn("OK");
		when(commands.get("evt:1")).thenAnswer(inv -> {
			ObjectMapper m = codec.mapper();
			return m.convertValue(original, Object.class);
		});

		template.set("evt:1", original);
		Event read = template.get("evt:1", Event.class);

		assertEquals(original, read);
		// The template's mapper (== codec's mapper) performed the conversion.
		assertSame(codec.mapper(), template.getObjectMapper());
	}

	@Test
	void missingKeyReadsAsNull() {
		JsonRedisCodec codec = new JsonRedisCodec();
		RedisClient client = mock(RedisClient.class);
		StatefulRedisConnection<String, Object> conn = mock(StatefulRedisConnection.class);
		RedisCommands<String, Object> commands = mock(RedisCommands.class);
		when(client.connect(codec)).thenReturn(conn);
		when(conn.sync()).thenReturn(commands);
		when(commands.get("absent")).thenReturn(null);

		SummerRedisTemplate template = new SummerRedisTemplate(client, codec);
		assertNull(template.get("absent", Event.class));
		assertNull(template.get("absent", new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {
		}));
		assertNull(template.getRaw("absent"));
	}

	@Test
	void codecEncodesAndDecodesThroughSingleMapper() {
		// A value that exercises JavaTimeModule must round-trip via the default
		// mapper without any per-call module registration.
		JsonRedisCodec codec = new JsonRedisCodec();
		Event original = new Event("t", 1, LocalDateTime.of(2024, 1, 1, 0, 0));
		Event decoded = codec.mapper().convertValue(codec.mapper().convertValue(original, Object.class), Event.class);
		assertEquals(original, decoded);
	}
}
