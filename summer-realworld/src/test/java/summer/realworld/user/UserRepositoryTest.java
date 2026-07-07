package summer.realworld.user;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import summer.realworld.user.User;

class UserRepositoryTest {

	private UserRepository userRepository;

	@BeforeEach
	void setUp() {
		userRepository = new UserRepository();
	}

	@Test
	void shouldSaveUser() {
		User user = new User();
		user.setUsername("testuser");
		user.setEmail("test@example.com");

		User saved = userRepository.save(user);

		assertNotNull(saved.getId());
		assertEquals("testuser", saved.getUsername());
	}

	@Test
	void shouldFindById() {
		User user = new User();
		user.setUsername("testuser");
		user.setEmail("test@example.com");
		User saved = userRepository.save(user);

		Optional<User> found = userRepository.findById(saved.getId());

		assertTrue(found.isPresent());
		assertEquals("testuser", found.get().getUsername());
	}

	@Test
	void shouldFindByEmail() {
		User user = new User();
		user.setUsername("testuser");
		user.setEmail("test@example.com");
		userRepository.save(user);

		Optional<User> found = userRepository.findByEmail("test@example.com");

		assertTrue(found.isPresent());
		assertEquals("testuser", found.get().getUsername());
	}

	@Test
	void shouldFindByUsername() {
		User user = new User();
		user.setUsername("testuser");
		user.setEmail("test@example.com");
		userRepository.save(user);

		Optional<User> found = userRepository.findByUsername("testuser");

		assertTrue(found.isPresent());
		assertEquals("test@example.com", found.get().getEmail());
	}

	@Test
	void shouldDeleteUser() {
		User user = new User();
		user.setUsername("testuser");
		user.setEmail("test@example.com");
		User saved = userRepository.save(user);

		userRepository.deleteById(saved.getId());

		assertTrue(userRepository.findById(saved.getId()).isEmpty());
		assertTrue(userRepository.findByEmail("test@example.com").isEmpty());
		assertTrue(userRepository.findByUsername("testuser").isEmpty());
	}
}
