package summer.data.redis.config;

import static org.junit.jupiter.api.Assertions.*;

import io.lettuce.core.api.sync.RedisCommands;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import summer.core.ApplicationContext;
import summer.runtime.RuntimeDiEngine;

@Testcontainers
public class RedisIntegrationIT {

	@Container
	public static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
			.withExposedPorts(6379);

	public record TestUserRecord(String name, int age, LocalDateTime registeredAt) {
	}

	@Test
	public void testRedisAutoConfigurationWithRealContainer() {
		// Set system property so RedisAutoConfiguration picks it up
		String redisUri = "redis://" + redis.getHost() + ":" + redis.getFirstMappedPort();
		System.setProperty("summer.redis.uri", redisUri);

		ApplicationContext context = new RuntimeDiEngine().create(RedisAutoConfiguration.class);

		try {

			// Retrieve the native commands interface
			RedisCommands<String, Object> commands = context.getBean(RedisCommands.class);
			assertNotNull(commands);

			// Perform real network operations
			String key = "test:user:1";
			TestUserRecord user = new TestUserRecord("Bob", 30, LocalDateTime.of(2023, 11, 20, 15, 0));

			// Set value
			String response = commands.set(key, user);
			assertEquals("OK", response);

			// Get value
			Object retrieved = commands.get(key);
			assertNotNull(retrieved);
			assertTrue(retrieved instanceof TestUserRecord);

			TestUserRecord retrievedUser = (TestUserRecord) retrieved;
			assertEquals("Bob", retrievedUser.name());
			assertEquals(30, retrievedUser.age());
			assertEquals(LocalDateTime.of(2023, 11, 20, 15, 0), retrievedUser.registeredAt());
		} finally {
			System.clearProperty("summer.redis.uri");
			context.destroy();
		}
	}
}
