package com.github.dropguard.summer.realworld.user;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.github.dropguard.summer.realworld.user.User;
import com.github.dropguard.summer.realworld.user.UserRepository;

class UserServiceTest {

	private UserService userService;
	private UserRepository userRepository;

	@BeforeEach
	void setUp() {
		userRepository = new UserRepository();
		userService = new UserService(userRepository);
	}

	@Test
	void shouldRegisterUser() {
		User user = userService.register("testuser", "test@example.com", "password123");

		assertNotNull(user);
		assertEquals("testuser", user.getUsername());
		assertEquals("test@example.com", user.getEmail());
		assertNotNull(user.getCreatedAt());
		assertNotNull(user.getUpdatedAt());
	}

	@Test
	void shouldThrowWhenEmailExists() {
		userService.register("user1", "test@example.com", "password123");

		assertThrows(RuntimeException.class, () -> userService.register("user2", "test@example.com", "password456"));
	}

	@Test
	void shouldThrowWhenUsernameExists() {
		userService.register("testuser", "user1@example.com", "password123");

		assertThrows(RuntimeException.class,
				() -> userService.register("testuser", "user2@example.com", "password456"));
	}

	@Test
	void shouldFindByEmail() {
		userService.register("testuser", "test@example.com", "password123");

		var found = userService.findByEmail("test@example.com");

		assertTrue(found.isPresent());
		assertEquals("testuser", found.get().getUsername());
	}

	@Test
	void shouldFindByUsername() {
		userService.register("testuser", "test@example.com", "password123");

		var found = userService.findByUsername("testuser");

		assertTrue(found.isPresent());
		assertEquals("test@example.com", found.get().getEmail());
	}

	@Test
	void shouldUpdateUser() {
		User user = userService.register("testuser", "test@example.com", "password123");

		User updated = userService.update(user, null, null, null, "New bio", "https://example.com/image.jpg");

		assertEquals("New bio", updated.getBio());
		assertEquals("https://example.com/image.jpg", updated.getImage());
	}
}
