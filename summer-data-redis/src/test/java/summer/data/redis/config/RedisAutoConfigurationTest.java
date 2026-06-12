package summer.data.redis.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import summer.core.ApplicationContext;
import summer.data.redis.codec.JsonRedisCodec;
import summer.runtime.RuntimeApplicationContext;

public class RedisAutoConfigurationTest {

	@Test
	public void testContextLoadsAndCreatesRedisBeans() {
		RedisClient mockClient = mock(RedisClient.class);
		StatefulRedisConnection mockConnection = mock(StatefulRedisConnection.class);
		RedisCommands mockCommands = mock(RedisCommands.class);

		when(mockClient.connect(any(JsonRedisCodec.class))).thenReturn(mockConnection);
		when(mockConnection.sync()).thenReturn(mockCommands);

		try (MockedStatic<RedisClient> mocked = mockStatic(RedisClient.class)) {
			mocked.when(() -> RedisClient.create(anyString())).thenReturn(mockClient);

			var ctx = new RuntimeApplicationContext();
			ctx.scan();
			ctx.initializeBeans();
			ApplicationContext context = ctx;

			// Then all beans should be created
			RedisProperties props = context.getBean(RedisProperties.class);
			assertNotNull(props);
			assertEquals("redis://localhost:6379", props.uri());

			RedisClient client = context.getBean(RedisClient.class);
			assertNotNull(client);

			StatefulRedisConnection connection = context.getBean(StatefulRedisConnection.class);
			assertNotNull(connection);

			RedisCommands commands = context.getBean(RedisCommands.class);
			assertNotNull(commands);

			// Cleanup resources cleanly via context
			try {
				context.close();
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
	}
}
