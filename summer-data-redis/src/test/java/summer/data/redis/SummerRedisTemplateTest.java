package summer.data.redis;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.api.sync.RedisCommands;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import summer.core.json.SummerObjectMapper;

@ExtendWith(MockitoExtension.class)
public class SummerRedisTemplateTest {

	@Mock
	private RedisCommands<String, Object> commands;

	private ObjectMapper objectMapper;
	private SummerRedisTemplate template;

	@BeforeEach
	void setUp() {
		objectMapper = SummerObjectMapper.create();
		template = new SummerRedisTemplate(commands, objectMapper);
	}

	@Test
	void testGetWithClassType() {
		// Given
		Map<String, Object> userData = Map.of("id", 1, "username", "gemini", "roles", List.of("admin", "user"));
		when(commands.get("user:1")).thenReturn(userData);

		// When
		UserCacheDTO result = template.get("user:1", UserCacheDTO.class);

		// Then
		assertNotNull(result);
		assertEquals(1L, result.id());
		assertEquals("gemini", result.username());
		assertEquals(List.of("admin", "user"), result.roles());
	}

	@Test
	void testGetWithTypeReference() {
		// Given
		List<String> roles = List.of("admin", "user");
		when(commands.get("user:1:roles")).thenReturn(roles);

		// When
		List<String> result = template.get("user:1:roles", new TypeReference<List<String>>() {
		});

		// Then
		assertNotNull(result);
		assertEquals(2, result.size());
		assertEquals("admin", result.get(0));
		assertEquals("user", result.get(1));
	}

	@Test
	void testGetWithClassTypeReturnsNullWhenKeyMissing() {
		// Given
		when(commands.get("missing:key")).thenReturn(null);

		// When
		UserCacheDTO result = template.get("missing:key", UserCacheDTO.class);

		// Then
		assertNull(result);
	}

	@Test
	void testGetWithTypeReferenceReturnsNullWhenKeyMissing() {
		// Given
		when(commands.get("missing:key")).thenReturn(null);

		// When
		List<String> result = template.get("missing:key", new TypeReference<List<String>>() {});

		// Then
		assertNull(result);
	}

	@Test
	void testSet() {
		// Given
		UserCacheDTO user = new UserCacheDTO(1L, "gemini", List.of("admin"));
		when(commands.set("user:1", user)).thenReturn("OK");

		// When
		template.set("user:1", user);

		// Then
		verify(commands).set("user:1", user);
	}

	@Test
	void testSetWithTtl() {
		// Given
		UserCacheDTO user = new UserCacheDTO(1L, "gemini", List.of("admin"));
		Duration ttl = Duration.ofHours(1);
		when(commands.set("user:1", user)).thenReturn("OK");
		when(commands.expire("user:1", 3600)).thenReturn(true);

		// When
		template.set("user:1", user, ttl);

		// Then
		verify(commands).set("user:1", user);
		verify(commands).expire("user:1", 3600);
	}

	@Test
	void testDelete() {
		// Given
		when(commands.del("key")).thenReturn(1L);

		// When
		boolean result = template.delete("key");

		// Then
		assertTrue(result);
	}

	@Test
	void testDeleteReturnsFalseWhenKeyMissing() {
		// Given
		when(commands.del("missing:key")).thenReturn(0L);

		// When
		boolean result = template.delete("missing:key");

		// Then
		assertFalse(result);
	}

	@Test
	void testExists() {
		// Given
		when(commands.exists("key")).thenReturn(1L);

		// When
		boolean result = template.exists("key");

		// Then
		assertTrue(result);
	}

	@Test
	void testExistsReturnsFalseWhenKeyMissing() {
		// Given
		when(commands.exists("missing:key")).thenReturn(0L);

		// When
		boolean result = template.exists("missing:key");

		// Then
		assertFalse(result);
	}

	@Test
	void testExpire() {
		// Given
		Duration ttl = Duration.ofMinutes(30);
		when(commands.expire("key", 1800)).thenReturn(true);

		// When
		boolean result = template.expire("key", ttl);

		// Then
		assertTrue(result);
	}

	// Test DTO
	public record UserCacheDTO(Long id, String username, List<String> roles) {
	}
}
