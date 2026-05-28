package summer.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import summer.test.annotation.SummerTest;

@SummerTest
class UserControllerTest {

	@Test
	void testUserControllerOperations(UserController userController) {
		assertNotNull(userController, "UserController should be injected");

		// 1. Create a user
		UserDto dto = new UserDto("Test User", "test@example.com");
		User created = userController.createUser(dto);
		assertNotNull(created.id());
		assertEquals("Test User", created.name());
		assertEquals("test@example.com", created.email());

		// 2. Get all users
		List<User> users = userController.getAllUsers();
		assertTrue(users.size() >= 1, "Should have at least 1 user");

		// 3. Get the user by ID
		User fetched = userController.getUser(created.id());
		assertEquals("Test User", fetched.name());

		// 4. Update the user
		UserDto updateDto = new UserDto("Updated User", "update@example.com");
		User updated = userController.updateUser(created.id(), updateDto);
		assertEquals("Updated User", updated.name());

		// 5. Delete the user
		userController.deleteUser(created.id());

		// Verify deletion by checking getAllUsers size
		List<User> usersAfterDelete = userController.getAllUsers();
		boolean exists = usersAfterDelete.stream().anyMatch(u -> u.id().equals(created.id()));
		assertTrue(!exists, "User should be deleted");
	}
}
