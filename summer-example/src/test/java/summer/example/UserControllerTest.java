package summer.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import summer.scanner.runtime.RuntimeDiEngine;
import summer.test.annotation.SummerTest;
import summer.web.Request;
import summer.web.WebContext;

@SummerTest(RuntimeDiEngine.class)
class UserControllerTest {

	@Test
	void testUserControllerOperations(UserController userController) {
		assertNotNull(userController, "UserController should be injected");

		// 1. Create a user
		UserDto dto = new UserDto("Test User", "test@example.com");
		WebContext createCtx = context("POST", "/users");
		userController.createUser(createCtx, dto);
		User created = (User) createCtx.resultObject();
		assertNotNull(created.id());
		assertEquals("Test User", created.name());
		assertEquals("test@example.com", created.email());

		// 2. Get all users
		WebContext listCtx = context("GET", "/users");
		userController.getAllUsers(listCtx);
		List<User> users = (List<User>) listCtx.resultObject();
		assertTrue(users.size() >= 1, "Should have at least 1 user");

		// 3. Get the user by ID
		WebContext getCtx = context("GET", "/users/" + created.id());
		userController.getUser(getCtx, created.id());
		User fetched = (User) getCtx.resultObject();
		assertEquals("Test User", fetched.name());

		// 4. Update the user
		UserDto updateDto = new UserDto("Updated User", "update@example.com");
		WebContext updateCtx = context("PUT", "/users/" + created.id());
		userController.updateUser(updateCtx, created.id(), updateDto);
		User updated = (User) updateCtx.resultObject();
		assertEquals("Updated User", updated.name());

		// 5. Delete the user
		WebContext deleteCtx = context("DELETE", "/users/" + created.id());
		userController.deleteUser(deleteCtx, created.id());

		// Verify deletion by checking getAllUsers size
		WebContext verifyCtx = context("GET", "/users");
		userController.getAllUsers(verifyCtx);
		List<User> usersAfterDelete = (List<User>) verifyCtx.resultObject();
		boolean exists = usersAfterDelete.stream().anyMatch(u -> u.id().equals(created.id()));
		assertTrue(!exists, "User should be deleted");
	}

	private WebContext context(String method, String path) {
		Request req = new Request(method, path, null, null, new byte[0]);
		return new WebContext(req);
	}
}
