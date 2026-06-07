package summer.data.redis;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.core.type.TypeReference;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import summer.data.redis.codec.JsonRedisCodec;

@Testcontainers
public class SummerRedisTemplateIT {

	@Container
	public static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
			.withExposedPorts(6379);

	private static RedisClient client;
	private static StatefulRedisConnection<String, Object> connection;
	private static SummerRedisTemplate template;

	@BeforeAll
	static void setUp() {
		String redisUri = "redis://" + redis.getHost() + ":" + redis.getFirstMappedPort();
		client = RedisClient.create(redisUri);
		connection = client.connect(new JsonRedisCodec());
		RedisCommands<String, Object> commands = connection.sync();
		template = new SummerRedisTemplate(commands);
	}

	@AfterAll
	static void tearDown() {
		if (connection != null) {
			connection.close();
		}
		if (client != null) {
			client.shutdown();
		}
	}

	@Test
	void testSetAndGetWithClassType() {
		// Given
		UserCacheDTO user = new UserCacheDTO(1L, "gemini", List.of("admin", "user"));

		// When
		template.set("test:user:1", user);
		UserCacheDTO result = template.get("test:user:1", UserCacheDTO.class);

		// Then
		assertNotNull(result);
		assertEquals(1L, result.id());
		assertEquals("gemini", result.username());
		assertEquals(List.of("admin", "user"), result.roles());
	}

	@Test
	void testSetAndGetWithTypeReference() {
		// Given
		List<String> roles = List.of("admin", "user", "moderator");

		// When
		template.set("test:roles", roles);
		List<String> result = template.get("test:roles", new TypeReference<List<String>>() {
		});

		// Then
		assertNotNull(result);
		assertEquals(3, result.size());
		assertEquals("admin", result.get(0));
		assertEquals("user", result.get(1));
		assertEquals("moderator", result.get(2));
	}

	@Test
	void testSetWithTtl() {
		// Given
		UserCacheDTO user = new UserCacheDTO(2L, "temp-user", List.of("guest"));
		Duration ttl = Duration.ofSeconds(2);

		// When
		template.set("test:user:temp", user, ttl);
		UserCacheDTO result = template.get("test:user:temp", UserCacheDTO.class);

		// Then
		assertNotNull(result);
		assertEquals(2L, result.id());

		// Wait for expiration
		try {
			Thread.sleep(2500);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}

		// Verify expiration
		UserCacheDTO expiredResult = template.get("test:user:temp", UserCacheDTO.class);
		assertNull(expiredResult);
	}

	@Test
	void testDelete() {
		// Given
		template.set("test:delete:key", "value");

		// When
		boolean deleted = template.delete("test:delete:key");

		// Then
		assertTrue(deleted);
		assertNull(template.get("test:delete:key", String.class));
	}

	@Test
	void testDeleteReturnsFalseWhenKeyMissing() {
		// When
		boolean deleted = template.delete("test:nonexistent:key");

		// Then
		assertFalse(deleted);
	}

	@Test
	void testExists() {
		// Given
		template.set("test:exists:key", "value");

		// When
		boolean exists = template.exists("test:exists:key");

		// Then
		assertTrue(exists);
	}

	@Test
	void testExistsReturnsFalseWhenKeyMissing() {
		// When
		boolean exists = template.exists("test:nonexistent:key");

		// Then
		assertFalse(exists);
	}

	@Test
	void testGetRaw() {
		// Given
		template.set("test:raw:key", "raw-value");

		// When
		Object result = template.getRaw("test:raw:key");

		// Then
		assertNotNull(result);
	}

	@Test
	void testGetReturnsNullWhenKeyMissing() {
		// When
		UserCacheDTO result = template.get("test:nonexistent:key", UserCacheDTO.class);

		// Then
		assertNull(result);
	}

	@Test
	void testComplexObjectWithNestedTypes() {
		// Given
		UserWithMetadata user = new UserWithMetadata(1L, "gemini", List.of("admin", "user"),
				LocalDateTime.of(2024, 1, 15, 10, 30));

		// When
		template.set("test:user:complex", user);
		UserWithMetadata result = template.get("test:user:complex", UserWithMetadata.class);

		// Then
		assertNotNull(result);
		assertEquals(1L, result.id());
		assertEquals("gemini", result.username());
		assertEquals(List.of("admin", "user"), result.roles());
		assertEquals(LocalDateTime.of(2024, 1, 15, 10, 30), result.createdAt());
	}

	@Test
	void testMapType() {
		// Given
		var userData = java.util.Map.of("id", 1, "name", "gemini", "active", true);

		// When
		template.set("test:map", userData);
		@SuppressWarnings("unchecked")
		var result = template.get("test:map", java.util.Map.class);

		// Then
		assertNotNull(result);
		assertEquals(1, result.get("id"));
		assertEquals("gemini", result.get("name"));
		assertEquals(true, result.get("active"));
	}

	// Test DTOs
	public record UserCacheDTO(Long id, String username, List<String> roles) {
	}

	public record UserWithMetadata(Long id, String username, List<String> roles, LocalDateTime createdAt) {
	}
}
