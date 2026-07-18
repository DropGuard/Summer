package summer.data.redis.config;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import summer.core.BeanContainer;
import summer.data.redis.SummerRedisTemplate;
import summer.test.Testing;

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

		// The connection is opened lazily by the template, so the container builds
		// without a live Redis; the real operations below exercise the connection.
		BeanContainer context = Testing.build();

		try {
			SummerRedisTemplate template = context.getBean(SummerRedisTemplate.class);
			assertNotNull(template);

			// Perform real network operations through the template
			String key = "test:user:1";
			TestUserRecord user = new TestUserRecord("Bob", 30, LocalDateTime.of(2023, 11, 20, 15, 0));

			template.set(key, user);

			TestUserRecord retrievedUser = template.get(key, TestUserRecord.class);
			assertNotNull(retrievedUser);
			assertEquals("Bob", retrievedUser.name());
			assertEquals(30, retrievedUser.age());
			assertEquals(LocalDateTime.of(2023, 11, 20, 15, 0), retrievedUser.registeredAt());
		} finally {
			System.clearProperty("summer.redis.uri");
			try {
				context.close();
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
	}
}
