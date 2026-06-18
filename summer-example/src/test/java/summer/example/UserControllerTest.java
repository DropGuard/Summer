package summer.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import summer.runtime.RuntimeApplicationContext;
import summer.web.HttpContext;
import summer.web.HttpMethod;
import summer.web.Request;

public class UserControllerTest {

	@summer.core.annotation.Configuration
	@summer.core.annotation.Replaces(summer.data.redis.config.RedisAutoConfiguration.class)
	public static class MockRedisConfiguration {
		@summer.core.annotation.Bean
		public io.lettuce.core.api.sync.RedisCommands<String, Object> mockRedisCommands() {
			return org.mockito.Mockito.mock(io.lettuce.core.api.sync.RedisCommands.class);
		}

	}

@Test
		void testUserControllerOperations() {
			var ctx = RuntimeApplicationContext.builder().registerComponent(MockRedisConfiguration.class)
					.registerComponent(UserController.class).build();

		UserController userController = ctx.getBean(UserController.class);
		assertNotNull(userController, "UserController should be injected");

		// 1. Create a user
		UserDto dto = new UserDto("Test User", "test@example.com");
		HttpContext createCtx = context(HttpMethod.POST, "/users");
		userController.createUser(createCtx, dto);
		User created = (User) createCtx.resultObject();
		assertNotNull(created.id());
		assertEquals("Test User", created.name());
		assertEquals("test@example.com", created.email());

		// 2. Get all users
		HttpContext listCtx = context(HttpMethod.GET, "/users");
		userController.getAllUsers(listCtx);
		List<User> users = (List<User>) listCtx.resultObject();
		assertTrue(users.size() >= 1, "Should have at least 1 user");

		// 3. Get the user by ID
		HttpContext getCtx = context(HttpMethod.GET, "/users/" + created.id());
		userController.getUser(getCtx, created.id());
		User fetched = (User) getCtx.resultObject();
		assertEquals("Test User", fetched.name());

		// 4. Update the user
		UserDto updateDto = new UserDto("Updated User", "update@example.com");
		HttpContext updateCtx = context(HttpMethod.PUT, "/users/" + created.id());
		userController.updateUser(updateCtx, created.id(), updateDto);
		User updated = (User) updateCtx.resultObject();
		assertEquals("Updated User", updated.name());

		// 5. Delete the user
		HttpContext deleteCtx = context(HttpMethod.DELETE, "/users/" + created.id());
		userController.deleteUser(deleteCtx, created.id());

		// Verify deletion by checking getAllUsers size
		HttpContext verifyCtx = context(HttpMethod.GET, "/users");
		userController.getAllUsers(verifyCtx);
		List<User> usersAfterDelete = (List<User>) verifyCtx.resultObject();
		boolean exists = usersAfterDelete.stream().anyMatch(u -> u.id().equals(created.id()));
		assertTrue(!exists, "User should be deleted");
	}

	private HttpContext context(HttpMethod method, String path) {
		Request req = new Request(method, path, null, null, new byte[0]);
		return new HttpContext(req);
	}
}
